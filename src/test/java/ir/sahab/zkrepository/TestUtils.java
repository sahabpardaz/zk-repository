package ir.sahab.zkrepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import ir.sahab.zk.client.RunnableWithException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.Assert;

/**
 * It extends the utilities provided in {@link Assert} class. These are some other methods which are not provided there
 * but we have found them useful.
 */
public class TestUtils {

    public static void repeatUntilAssertionsSuccess(
            AssertionStatements assertionStatements, int timeoutMillis) throws Exception {
        repeatUntilAssertionsSuccess(assertionStatements, timeoutMillis, 0);
    }

    /**
     * In unit tests, sometimes we have to sleep before checking the result of an asynchronous work. But how much sleep?
     * We should use a number which is 'big enough' to assure us. But this 'big enough' number is so much bigger than
     * the actual number which is required in most of the runs. So it makes the unit tests slow. The workaround we have
     * found is to repeat the assertion statements until they succeed or until we have reach to the so-called 'big
     * enough' timeout. This method, is for this reason. It gives a callback (containing the assertion statements) and
     * repeats them until assertions success or reaching the timeout bound. If this timeout is reached, we throw an
     * error.
     *
     * @param assertionStatements A callback containing the assertion statements (i.e., the statements which throw
     *      {@link AssertionError}, for examples the assert methods provided by {@link Assert} class).
     * @param timeoutMillis A limit on how much time this method can take.
     * @param sleepBetweenTries The sleep between each repeat.
     * @throws Exception If the callback throws any exception except than {@link AssertionError}, we simply throw it up.
     */
    public static void repeatUntilAssertionsSuccess(AssertionStatements assertionStatements,
            int timeoutMillis, int sleepBetweenTries)
            throws Exception {
        long startTime = System.currentTimeMillis();

        do {
            try {
                assertionStatements.run();
                return;  // It was successful, no more tries required.
            } catch (AssertionError error) {
                if (System.currentTimeMillis() - startTime < timeoutMillis) {
                    Thread.sleep(sleepBetweenTries);
                } else {
                    throw error;
                }
            }
        } while (true);
    }

    /**
     * Utility test method that calls a Callable and expects it to throw an exception before the given timeout.
     */
    public static void assertThrows(Class<? extends Throwable> expected, Callable<?> callback,
            int timeout, TimeUnit timeoutUnit) {
        // Call the callback in a separate thread to test the timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = null;
        try {
            future = executor.submit(callback);

            try {
                // We expect the callback to throw an exception.
                future.get(timeout, timeoutUnit);
                fail("Expected " + expected.getName() + ", but finished successfully");
            } catch (ExecutionException e) {
                // We expect a ExecutionException -> expectedThrowableType
                Assert.assertEquals(e.getCause().getClass(), expected);
            } catch (InterruptedException e) {
                throw new AssertionError("Expected " + expected.getName() + ", but interrupted", e);
            } catch (TimeoutException e) {
                fail("Expected " + expected.getName() + ", but timed out");
            }
        } finally {
            // Check future to prevent getting NullPointerException here when an exception is being
            // thrown and "finally" is running
            if (future != null) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    /**
     * Runs the runnable and expects an exception of the specified type.
     *
     * @param expected Type of exception that is expected to be thrown by the runnable.
     * @param runnable Runnable to be executed.
     * @see #assertThrows(Class, RunnableWithException, Consumer)
     */
    public static <T extends Throwable> void assertThrows(
            Class<T> expected, RunnableWithException<Throwable> runnable) {
        assertThrows(expected, runnable, t -> {
        });
    }

    /**
     * Runs the runnable and expects an exception of the specified type. Furthermore, it can provide more custom
     * assertions to be checked on the thrown exception.
     *
     * @param expected Type of exception that is expected to be thrown by the runnable.
     * @param runnable Runnable to be executed.
     * @param exceptionValidator Further validation of the thrown exception.
     */
    public static <T extends Throwable> void assertThrows(
            Class<T> expected, RunnableWithException<Throwable> runnable, Consumer<T> exceptionValidator) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            StringWriter stacktrace = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stacktrace);
            throwable.printStackTrace(printWriter);
            assertEquals(stacktrace.toString(), expected, throwable.getClass());
            @SuppressWarnings("unchecked")
            T t = (T) throwable;
            exceptionValidator.accept(t);
            return;
        }
        fail(String.format("Expected %s but no exception was thrown", expected));
    }

    /**
     * An interface for the callback functions which supposed to contain assertion statements (i.e., the statements
     * which throw {@link AssertionError}, for examples the assert methods provided by {@link Assert} class).
     */
    @FunctionalInterface
    public interface AssertionStatements {

        void run() throws Exception;
    }
}