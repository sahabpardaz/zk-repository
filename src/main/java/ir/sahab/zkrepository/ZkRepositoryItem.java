package ir.sahab.zkrepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.ParseException;

/**
 * An interface for any class that is required to be stored in Zookeeper as a repository.
 */
public interface ZkRepositoryItem<T extends ZkRepositoryItem<T>> {

    /**
     * Returns the ID of the object. The returned value is used as the name of ZK node representing the item.
     */
    int getId();


    /**
     * Validates the domain item to be called before add/update operations.
     *
     * @throws RuntimeException an appropriate runtime exception if provided item does not pass validation
     *      requirements.
     */
    void validate(ZkRepository<T> repository);

    /**
     * Returns the string representation of the object content. The returned value is used as the value of ZK node
     * representing an item.
     */
    default String serialize() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new AssertionError("Unexpected exception while serializing a DPI to JSON.", ex);
        }
    }

    /**
     * Reads the given string representation of the object produced by {@link #serialize} and returns it.
     */
    default T deserialize(String serializedContent, Class<T> clazz) throws ParseException {
        try {
            return new ObjectMapper().readValue(serializedContent, clazz);
        } catch (IOException e) {
            throw new ParseException("Unable to parse JSON: " + e.getMessage(), 0);
        }
    }
}