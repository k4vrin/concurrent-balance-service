package dev.kavrin.balance.operation.application.execution;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Controls elapsed time and backoff observations for portable recovery tests.
 *
 * <p>Tests may advance the clock or request interruption. A pause records the supplied jitter
 * ceiling and advances time by that ceiling without sleeping, allowing deterministic budget checks.
 * Backoff inside a transaction fails immediately. Mutable state is confined to one test instance.
 */
final class DeterministicRecoveryRuntime implements RecoveryRuntime {
    long now;
    boolean interrupt;
    final List<Long> delays = new ArrayList<>();

    @Override
    public long nanoTime() { return now; }

    @Override
    public void pause(long maximumMillis) throws InterruptedException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new AssertionError("Backoff must be outside the transaction");
        }
        if (interrupt) { throw new InterruptedException("cancelled"); }
        delays.add(maximumMillis);
        now += maximumMillis * 1_000_000L;
    }
}
