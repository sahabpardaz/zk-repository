package ir.sahab.zkrepository;

/**
 * An exception that is thrown when trying to add a duplicate item in {@link ZkRepository}.
 */
public class DuplicateNodeException extends Exception {

    private static final long serialVersionUID = 6302673254488497183L;

    public DuplicateNodeException(String message, Throwable cause) {
        super(message, cause);
    }
}