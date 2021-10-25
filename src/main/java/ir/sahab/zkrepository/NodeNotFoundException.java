package ir.sahab.zkrepository;

/**
 * An exception that is thrown when trying to retrieve or manipulate an item in {@link ZkRepository} but it is not
 * found.
 */
public class NodeNotFoundException extends Exception {

    private static final long serialVersionUID = 6302673254488497183L;

    public NodeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}