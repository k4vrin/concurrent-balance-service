package dev.kavrin.balance.operation.application.execution;

/**
 * Supplies monotonic time and interruptible backoff to the mutation recovery loop.
 *
 * <p>Time readings must use a consistent nanosecond origin for elapsed-time calculations. Backoff
 * runs outside transactions and receives an inclusive maximum delay in milliseconds. Production
 * uses full jitter; test implementations may advance a controlled clock or release a fixture lock.
 */
public interface RecoveryRuntime {
    long nanoTime();
    void pause(long maximumMillis) throws InterruptedException;
}
