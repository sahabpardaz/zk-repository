package ir.sahab.zkrepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Sample entity to persist in repository.
 */
public class TestEntity implements ZkRepositoryItem<TestEntity> {

    private int id;
    private String name;

    @Override
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public void validate(ZkRepository<TestEntity> repository) {
        List<String> errors = new ArrayList<>();
        if (getId() < 0 || getId() > Short.MAX_VALUE) {
            errors.add("ID of TestEntity must be a positive number less than " + Short.MAX_VALUE + ".");
        }
        if (StringUtils.isEmpty(getName())) {
            errors.add("Name of TestEntity must not be empty.");
        }
        if (!errors.isEmpty()) {
            throw new AssertionError("Invalid TestEntity: " + this + String.join("\n", errors));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TestEntity)) {
            return false;
        }
        TestEntity that = (TestEntity) o;
        return id == that.id && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    public static class Builder {

        private final TestEntity entity = new TestEntity();

        public Builder setId(int id) {
            entity.id = id;
            return this;
        }

        public Builder setName(String name) {
            entity.name = name;
            return this;
        }

        public TestEntity build() {
            return entity;
        }
    }
}