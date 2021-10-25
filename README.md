# Zookeeper Repository
[![Tests](https://github.com/sahabpardaz/zk-repository/actions/workflows/maven.yml/badge.svg?branch=main)](https://github.com/sahabpardaz/zk-repository/actions/workflows/maven.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=coverage)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=duplicated_lines_density)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=security_rating)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=reliability_rating)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=sqale_index)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sahabpardaz_zk-repository&metric=alert_status)](https://sonarcloud.io/dashboard?id=sahabpardaz_zk-repository)
[![JitPack](https://jitpack.io/v/sahabpardaz/zk-repository.svg)](https://jitpack.io/#sahabpardaz/zk-repository)

Using this library, and writing a few lines of code, you can manage your own domain objects in ZooKeeper. It provides
CRUD operations and change notifications out of the box.

## Who should use it?

There are many relational and NoSQL data stores you can use for persisting data, but the main purpose of ZooKeeper is
for distributed coordination and configuration. So it makes sense to ask why should I use ZooKeeper as a persistence
store? That's right. In most cases ZooKeeper is not a good choice for this reason, but for specific class of problems,
we have found it useful, when you have a problem that met *all* of these conditions:
<ul>
 <li> You need online change notifications. Most relational and NoSQL databases do not provide a native push
 notification feature. One solution is to simulate push notification by periodic polls. But in ZooKeeper, you can simply
 listen for the changes.
 <li> There are limited number of objects (i.e., thousands not billions). This library is supposed to handle all objects
 in memory. It even provides a method that gets the whole snapshot of objects in a Map data structure.
 <li> You do not need facilities to check or force constraints like foreign keys. You can have multiple type of objects
 as different parallel repositories but you do not get automatic constraint checks about relations between objects.
</ul>

## Sample usage

As an example, suppose you have a simple data type (e.g., Car) and you want to persist  it using `ZkRepository`.
At first, you should extend your data type and implement two methods: a method that identifies the ID of a given object
(that is used as node name in ZK), and a method that is used for validation of objects before persistence on add and
update operations.

```java
public class Car implements ZkRepositoryItem<Car> {
  private int id;
  private String model;
  private String color;
  ...

  @Override
  int getId() {
    return id;
  }

   @Override
   public void validate(ZkRepository<Car> repository) {
     if (isEmptyOrNull(model)) {
         throw ValidationException("Car model is empty!");
     }
  }
}
```

Note that by default, a JSON representation of the objects will be stored in ZooKeeper. You can change the default
serialization/deserialization mechanism by overriding `serialize()` and `deserialize()` methods.

After defining the car class, then implement the car repository by extending the general repository. You should call the
super constructor by the ZK server addresses, and the root node in ZooKeeper in which the data objects should be kept:

```java
public class CarRepository extends ZkRepository<Car> {
   public CarRepository(String zkAddresses) {
      super(zkAddresses, "apps_root/cars");
   }
}
```

Now you are ready to use it:

```java
// Initializing the repository:
CarRepository carRepo = new CarRepository("zookeeper-server:2181");
carRepo.start(true);

// Add operation:
Car ferrari = new Car(1, "Ferrari", "Red");
Car ford = new Car(2, "Ford", "Blue");
carRepo.add(ferrari);
carRepo.add(ford);

// Get operation:
assertEquals(ferrari, carRepo.get(ferrari.getId());
assertEquals(ford, carRepo.get(ford.getId());

// Update operation:
ferrari.setColor("Gold");
carRepo.update(ferrari);
assertEquals("Gold", carRepo.get(ferrari.getId()).getColor());

// Snapshot:
Map<Integer, Car> snapshot = carRepo.getSnapshot();
assertEquals(ferrari, snapshot.get(ferrari.getId());
assertEquals(ford, snapshot.get(ford.getId());

// Remove operation:
carRepo.remove(ferrari.getId());
Map<Integer, Car> snapshot = carRepo.getSnapshot();
assertNull(snapshot.get(ferrari.getId());

// Change notification:
carRepo.registerChangeCallback(
   () -> System.out("Cars are changed. Current snapshot of values: %s", carRepo.getSnapshot().toString()));
```