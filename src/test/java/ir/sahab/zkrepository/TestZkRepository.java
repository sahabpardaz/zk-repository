package ir.sahab.zkrepository;

/**
 * Test zookeeper repository for CRUD operations over {@link TestEntity}.
 */
public class TestZkRepository extends ZkRepository<TestEntity> {

    // Constants related to TestEntity repository paths in zookeeper
    public static final String ZK_TEST_REPOSITORY_ROOT_PATH = "/test_repository";

    public TestZkRepository(String zkAddresses) {
        super(zkAddresses, ZK_TEST_REPOSITORY_ROOT_PATH, TestEntity.class);
    }
}