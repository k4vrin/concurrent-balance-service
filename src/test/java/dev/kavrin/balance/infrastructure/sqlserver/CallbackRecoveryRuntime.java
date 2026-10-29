package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.operation.application.execution.RecoveryRuntime;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a fixture callback at the recovery loop's outside-transaction backoff boundary.
 *
 * <p>The callback can inspect independently committed state or release a blocker before another
 * attempt. Time remains monotonic, but no jitter sleep is performed, keeping fixture coordination
 * deterministic. Calling backoff while a transaction is active fails the test immediately.
 */
final class CallbackRecoveryRuntime implements RecoveryRuntime {
    private final Runnable callback;

    CallbackRecoveryRuntime(Runnable callback) { this.callback = callback; }

    @Override
    public long nanoTime() { return System.nanoTime(); }

    @Override
    public void pause(long maximumMillis) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssertionError("Backoff inside a transaction");
        }
        callback.run();
    }
}
