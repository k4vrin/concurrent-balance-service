package dev.kavrin.balance.operation.application.execution;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides production recovery timing through the JVM monotonic clock and thread-local full jitter.
 *
 * <p>Each pause samples an inclusive delay between zero and the supplied maximum. Interruption
 * propagates to the recovery loop, which restores the thread's interrupt status and stops admitting
 * work. The implementation is stateless and may be shared by concurrent requests.
 */
final class SystemRecoveryRuntime implements RecoveryRuntime {
    @Override
    public long nanoTime() { return System.nanoTime(); }

    @Override
    public void pause(long maximumMillis) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(maximumMillis + 1));
    }
}
