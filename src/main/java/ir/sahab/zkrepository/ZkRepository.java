package ir.sahab.zkrepository;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import ir.sahab.cleanup.Cleanups;
import ir.sahab.zk.client.ZkClient;
import ir.sahab.zk.client.ZkClientException;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.zookeeper.KeeperException.Code;

/**
 * The abstract class for the specific repository classes to manage a list of desired type of domain object in
 * ZooKeeper. It provides CRUD operations and change notifications out of the box.
 * <p>
 * There are many relational and NoSQL data stores you can use for persisting data, but the main purpose of ZooKeeper is
 * for distributed coordination and configuration. So it makes sense to ask why should I use ZooKeeper as a persistence
 * store? That's right. In most cases ZooKeeper is not a good choice for this reason, but for specific class of
 * problems, we have found it useful, when you have a problem that met *all* of these conditions:
 * <ul>
 *  <li> You need online change notifications. Most relational and NoSQL databases do not provide a native push
 *  notification feature. One solution is to simulate push notification by periodic polls. But in ZooKeeper, you can
 *  simply listen for the changes.
 *  <li> There are limited number of objects (i.e., thousands not billions). This library is supposed to handle all
 *  objects in memory. It even provides a method that gets the whole snapshot of objects in a Map data structure.
 *  <li> You do not need facilities to check or force constraints like foreign keys. You can have multiple type of
 *  objects as different parallel repositories but you do not get automatic constraint checks about relations between
 *  objects.
 * </ul>
 * </p>
 * <p>
 * As an example, suppose you have a simple data type (e.g., Car) and you want to persist  it using `ZkRepository`.
 * At first, you should extend your data type and implement two methods: a method that identifies the ID of a given
 * object (that is used as node name in ZK), and a method that is used for validation of objects before persistence on
 * add/update operations.
 * </p>
 * <pre>{@code
 * public class Car implements ZkRepositoryItem {
 *   private int id;
 *   private String model;
 *   private String color;
 *   ...
 *
 *   @Override
 *   int getId() {
 *     return id;
 *   }
 *
 *   @Override
 *    public void validate(ZkRepository<Car> repository) {
 *      if (isEmptyOrNull(model)) {
 *          throw ValidationException("Car model is empty!");
 *      }
 *   }
 * }
 * }
 * </pre>
 * <p>
 * Note that by default, a JSON representation of the objects will be stored in ZooKeeper. You can change the default
 * serialization/deserialization mechanism by overriding `serialize()` and `deserialize()` methods.
 * </p>
 * <p>
 * After defining the car class, then implement the car repository by extending the general repository. You should call
 * the super constructor by the ZK server addresses, and the root node in ZooKeeper in which the data objects should be
 * kept:
 * </p>
 * <pre>{@code
 * public class CarRepository extends ZkRepository<Car> {
 *    public CarRepository(String zkAddresses) {
 *       super(zkAddresses, "apps_root/cars");
 *    }
 * }
 * }
 * </pre>
 * <p>
 * Now you are ready to use it:
 * </p>
 * <pre>{@code
 * // Initializing the repository:
 * CarRepository carRepo = new CarRepository("zookeeper-server:2181");
 * carRepo.start(true);
 *
 * // Add operation:
 * Car ferrari = new Car(1, "Ferrari", "Red");
 * Car ford = new Car(2, "Ford", "Blue");
 * carRepo.add(ferrari);
 * carRepo.add(ford);
 *
 * // Get operation:
 * assertEquals(ferrari, carRepo.get(ferrari.getId());
 * assertEquals(ford, carRepo.get(ford.getId());
 *
 * // Update operation:
 * ferrari.setColor("Gold");
 * carRepo.update(ferrari);
 * assertEquals("Gold", carRepo.get(ferrari.getId()).getColor());
 *
 * // Snapshot:
 * Map<Integer, Car> snapshot = carRepo.getSnapshot();
 * assertEquals(ferrari, snapshot.get(ferrari.getId());
 * assertEquals(ford, snapshot.get(ford.getId());
 *
 * // Remove operation:
 * carRepo.remove(ferrari.getId());
 * Map<Integer, Car> snapshot = carRepo.getSnapshot();
 * assertNull(snapshot.get(ferrari.getId());
 *
 * // Change notification:
 * carRepo.registerChangeCallback(
 *    () -> System.out("Cars are changed. Current snapshot of values: %s", carRepo.getSnapshot().toString()));
 * }</pre>
 */
public abstract class ZkRepository<T extends ZkRepositoryItem<T>> implements Closeable {

    private static final int ZK_CONNECTION_TIMEOUT_MILLIS = 5000;
    private static final int ZK_SESSION_TIMEOUT_MILLIS = 5000;
    private static final int ZK_NUM_RETRIES_FOR_FAILED_OPS = 3;
    private static final int ZK_SLEEP_BETWEEN_RETRIES_MILLIS = 1000;

    protected final ZkClient zkClient = new ZkClient();
    private final List<Runnable> itemChangeCallbacks = new CopyOnWriteArrayList<>();
    private PathChildrenCache pathChildrenCache;
    private final String zkAddresses;
    private final String zkRootPath;
    private final Class<T> clazz;

    protected ZkRepository(String zkAddresses, String zkRootPath, Class<T> clazz) {
        this.zkAddresses = zkAddresses;
        this.zkRootPath = zkRootPath;
        this.clazz = clazz;
    }

    /**
     * Specifies root path address for it's domain class type.
     *
     * @return entity root path address
     */
    protected final String getZkRootPath() {
        return zkRootPath;
    }

    /**
     * Creates zookeeper's root path related to this repository.
     *
     * <p>This method is idempotent and does nothing if the root path already exists. Contrary to
     * the other methods, it is not required to call {@link #start()} method before calling this method. Also note that
     * it is just for the first day setup and you do not need to call it each time you want to start the ZK repository
     * object.
     */
    public void initRootNode() throws ZkClientException, InterruptedException {
        try (ZkClient localZkClient = new ZkClient()) {
            localZkClient.start(zkAddresses);
            if (!localZkClient.exists(getZkRootPath())) {
                localZkClient.addPersistentNode(getZkRootPath(), "", true);
            }
        }
    }

    public void start() throws ZkClientException, InterruptedException {
        zkClient.start(zkAddresses, ZK_CONNECTION_TIMEOUT_MILLIS, ZK_SESSION_TIMEOUT_MILLIS,
                ZK_NUM_RETRIES_FOR_FAILED_OPS, ZK_SLEEP_BETWEEN_RETRIES_MILLIS);

        Preconditions.checkState(zkClient.exists(getZkRootPath()),
                "Root paths does not exist in zookeeper tree: " + getZkRootPath());

        initCache();
    }

    private void initCache() throws ZkClientException {
        pathChildrenCache = zkClient.newPathChildrenCache(getZkRootPath());
        pathChildrenCache.getListenable().addListener((client, event) -> {
            if (nodesChanged(event)) {
                itemChangeCallbacks.forEach(Runnable::run);
            }
        });
        try {
            pathChildrenCache.start();
        } catch (Exception ex) {
            throw new ZkClientException("Error in starting the cache for root path: " + getZkRootPath(), ex);
        }
    }

    /**
     * Adds a new item to repository.
     *
     * @throws DuplicateNodeException if an item with same ID already exists in repository
     */
    public void add(T item) throws InterruptedException, IOException, DuplicateNodeException {
        item.validate(this);
        try {
            zkClient.addPersistentNode(joinPaths(getZkRootPath(), "" + item.getId()), item.serialize());
        } catch (ZkClientException e) {
            if (e.getZkErrorCode() == Code.NODEEXISTS) {
                throw new DuplicateNodeException(
                        "An item with same ID \"" + item.getId() + "\" exists in repository", e);
            }
            throw new IOException("Error occurred during adding ZK item: " + item, e);
        }
    }

    /**
     * Updates an existing ZK repository item.
     *
     * @throws AssertionError if provided item does not pass validation requirements
     * @throws NodeNotFoundException if can't find an item with provided ID
     */
    public void update(T item) throws InterruptedException, IOException, NodeNotFoundException {
        item.validate(this);
        try {
            zkClient.setData(joinPaths(getZkRootPath(), "" + item.getId()), item.serialize());
        } catch (ZkClientException e) {
            if (e.getZkErrorCode() == Code.NONODE) {
                throw new NodeNotFoundException("Item with ID: " + item.getId() + " not found for update.", e);
            }
            throw new IOException("Error occurred during updating ZK item: " + item, e);
        }
    }

    /**
     * Deletes an item with provided ID from repository.
     *
     * @throws NodeNotFoundException if can't find item with provided ID
     */
    public void remove(int id) throws InterruptedException, IOException, NodeNotFoundException {
        try {
            zkClient.getData(joinPaths(getZkRootPath(), "" + id));
        } catch (ZkClientException e) {
            if (e.getZkErrorCode() == Code.NONODE) {
                throw new NodeNotFoundException("No item with ID: " + id + " were found to delete.", e);
            }
            throw new IOException("Error occurred during removing ZK item with ID: " + id, e);
        }

        try {
            zkClient.remove(joinPaths(getZkRootPath(), "" + id));
        } catch (ZkClientException e) {
            if (e.getZkErrorCode() != Code.NONODE) {
                throw new IOException("Error occurred during removing ZK item: " + id, e);
            }
        }
    }

    /**
     * Gets an ZK item with provided ID.
     *
     * @throws NodeNotFoundException if can't find item with provided ID.
     */
    public T get(int id) throws NodeNotFoundException, IOException, InterruptedException {
        try {
            T item = clazz.getConstructor().newInstance();
            return item.deserialize(new String(zkClient.getData(joinPaths(getZkRootPath(), "" + id))), clazz);
        } catch (ZkClientException e) {
            if (e.getZkErrorCode() == Code.NONODE) {
                throw new NodeNotFoundException("No item with ID: " + id + " were found.", e);
            }
            throw new IOException("Error occurred during getting ZK item with ID: " + id, e);
        } catch (ParseException | NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            throw new AssertionError("Exception while deserialize JSON to ZkRepositoryItem.", e);
        }
    }

    /**
     * Returns a snapshot of all items in repository as a map. Key of map is ID of item. Map would be empty if there is
     * no items in repository.
     */
    public Map<Integer, T> getSnapshot() throws InterruptedException, IOException, NodeNotFoundException {
        Map<Integer, T> snapShot = new HashMap<>();
        List<String> childrenNodeNames;
        try {
            childrenNodeNames = zkClient.getChildren(getZkRootPath());
        } catch (ZkClientException e) {
            throw new IOException("Error occurred during getting snapshot", e);
        }
        for (String childName : childrenNodeNames) {
            int childKey;
            try {
                childKey = Integer.parseInt(childName);
            } catch (NumberFormatException e) {
                throw new AssertionError("The name of ZK node for item is not numeric "
                        + "(indicating the item ID)", e);
            }
            T childValue = get(childKey);
            snapShot.put(childKey, childValue);
        }
        return snapShot;
    }

    /**
     * Registers the callback that will be called when any items changed.
     */
    public void registerChangeCallback(Runnable callback) {
        itemChangeCallbacks.add(callback);
    }

    /**
     * Checks if this event says that child nodes were changed or not.
     */
    private static boolean nodesChanged(PathChildrenCacheEvent event) {
        return event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)
                || event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)
                || event.getType().equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)
                || event.getType().equals(PathChildrenCacheEvent.Type.INITIALIZED);
    }

    /**
     * Joins the sub-paths by putting "/" between them if necessary.
     *
     * @param subPaths sub paths
     * @return the joined path
     */
    private static String joinPaths(String... subPaths) {
        Preconditions.checkArgument(subPaths != null && subPaths.length != 0);
        List<String> pathList = new ArrayList<>();
        // We don't want to prepend a '/' to the result on our own, unless if the first subpath
        // starts with a '/' itself.
        boolean appendSlash = false;
        if (subPaths[0] != null && subPaths[0].startsWith("/")) {
            appendSlash = true;
        }
        for (int i = 0; i < subPaths.length; i++) {
            String sp = subPaths[i];
            if (Strings.isNullOrEmpty(sp)) {
                throw new AssertionError(String.format("Invalid path: %s", Arrays.toString(subPaths)));
            }
            if (sp.startsWith("/")) {
                sp = sp.substring(1);
            }
            if (sp.endsWith("/")) {
                sp = sp.substring(0, sp.length() - 1);
            }
            if (i != 0 || !sp.isEmpty()) {
                pathList.add(sp);
            }
        }
        String finalPath = Joiner.on("/").join(pathList);
        return appendSlash ? "/" + finalPath : finalPath;
    }

    /**
     * Closes PathChildrenCache and Zookeeper client connection.
     */
    @Override
    public void close() throws IOException {
        Cleanups.of(pathChildrenCache, zkClient).doAll();
    }
}