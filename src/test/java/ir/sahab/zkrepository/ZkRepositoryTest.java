package ir.sahab.zkrepository;

import static ir.sahab.zkrepository.TestUtils.assertThrows;
import static ir.sahab.zkrepository.TestUtils.repeatUntilAssertionsSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import ir.sahab.uncaughtexceptionrule.UncaughtExceptionRule;
import ir.sahab.zookeeperrule.ZooKeeperRule;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ZkRepositoryTest {

    @ClassRule
    public static final ZooKeeperRule zkServer = new ZooKeeperRule();

    @Rule
    public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();

    private TestZkRepository testZkRepository;
    private Map<Integer, TestEntity> testEntityCache;

    @Before
    public void setUp() throws Exception {
        testZkRepository = new TestZkRepository(zkServer.getAddress());
        testZkRepository.initRootNode();
        testZkRepository.start();
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (testZkRepository != null) {
                for (Integer id : testZkRepository.getSnapshot().keySet()) {
                    testZkRepository.remove(id);
                }
                testZkRepository.close();
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to close resources.");
        }
    }

    @Test
    public void testEntityCrud() throws Exception {

        // Try to add an invalid TestEntity (ID is -1).
        TestEntity invalidEntity = new TestEntity.Builder().setName("invalidEntity").setId(-1).build();
        assertThrows(AssertionError.class, () -> testZkRepository.add(invalidEntity));

        // Try to add an invalid TestEntity (without Name).
        TestEntity invalidTestEntity2 = new TestEntity.Builder().setId(1).build();
        assertThrows(AssertionError.class, () -> testZkRepository.add(invalidTestEntity2));

        // Add a valid TestEntity.
        TestEntity testEntity1 = new TestEntity.Builder().setId(1).setName("testEntity1").build();
        testZkRepository.add(testEntity1);

        // Get a TestEntity from repository and check it.
        assertEquals(testEntity1, testZkRepository.get(1));

        // Add a TestEntity with duplicate Id.
        TestEntity testEntityDuplicateId = new TestEntity.Builder().setId(1).setName("testEntityDuplicateId").build();
        assertThrows(DuplicateNodeException.class, () -> testZkRepository.add(testEntityDuplicateId));

        // Try to get a TestEntity with Id that does not exist in repository.
        assertThrows(NodeNotFoundException.class, () -> testZkRepository.get(2));

        // Add another valid TestEntity.
        TestEntity testEntity2 = new TestEntity.Builder().setId(2).setName("testEntity2").build();
        testZkRepository.add(testEntity2);

        // Update a TestEntity.
        TestEntity testEntityToUpdate = new TestEntity.Builder().setId(2).setName("TestEntity2Update").build();
        testZkRepository.update(testEntityToUpdate);

        // Get updated TestEntity and check it.
        assertEquals(testEntityToUpdate, testZkRepository.get(2));

        // Try to update a TestEntity with Id that does not exist in repository.
        TestEntity testEntityNotExistForUpdate = new TestEntity.Builder().setId(3)
                .setName("testEntityNotExistForUpdate").build();
        assertThrows(NodeNotFoundException.class, () -> testZkRepository.update(testEntityNotExistForUpdate));

        // Provide an invalid TestEntity to update.
        assertThrows(AssertionError.class, () -> testZkRepository.update(invalidEntity));

        // Delete a TestEntity.
        testZkRepository.remove(2);

        // Check deletion by a get.
        assertThrows(NodeNotFoundException.class, () -> testZkRepository.get(2));

        // Try to delete a TestEntity with Id that does not exist in repository.
        assertThrows(NodeNotFoundException.class, () -> testZkRepository.remove(3));
    }

    @Test
    public void testNotificationAndSnapshot() throws Exception {
        final AtomicInteger numTestEntityCallbacksCalled = new AtomicInteger(0);

        // Register callbacks on testEntity repository.
        testZkRepository.registerChangeCallback(() -> {
            try {
                testEntityCache = testZkRepository.getSnapshot();
                numTestEntityCallbacksCalled.incrementAndGet();
            } catch (InterruptedException | IOException | NodeNotFoundException e) {
                fail("Unexpected exception: " + e.getMessage());
            }
        });

        // Add a testEntity to repository.
        TestEntity testEntity = new TestEntity.Builder().setId(1).setName("TestEntity1").build();
        testZkRepository.add(testEntity);

        // The repository callback should be called once and repository caches must be updated.
        repeatUntilAssertionsSuccess(() -> {
            assertEquals(1, numTestEntityCallbacksCalled.get());
            assertEquals(testEntity, testEntityCache.get(1));
            assertEquals(1, testEntityCache.size());
        }, 1000);

        // Update data.
        TestEntity updatedTestEntity = new TestEntity.Builder().setId(1).setName("TestEntityUpdated").build();

        testZkRepository.update(updatedTestEntity);

        // The repository callback should be called 2 times till now and repository caches must be updated.
        repeatUntilAssertionsSuccess(() -> {
            assertEquals(2, numTestEntityCallbacksCalled.get());
            assertEquals(updatedTestEntity, testEntityCache.get(1));
            assertEquals(1, testEntityCache.size());
        }, 1000);

        // Delete data.
        testZkRepository.remove(1);

        // The repository callback should be called 3 times till now and repository caches must be updated.
        repeatUntilAssertionsSuccess(() -> {
            assertEquals(3, numTestEntityCallbacksCalled.get());
            assertNull(testEntityCache.get(1));
            assertEquals(0, testEntityCache.size());
        }, 1000);
    }
}