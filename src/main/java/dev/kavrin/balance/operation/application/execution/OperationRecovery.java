package dev.kavrin.balance.operation.application.execution;

import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates replay, stable attempt preparation and bounded recovery for one mutation request.
 *
 * <p>Writing retries require both reported transaction rollback and an eligible SQL Server failure.
 * Identity recovery occurs in fresh transactions; unknown completion permits recovery reads only,
 * even when those reads repeatedly find no outcome (ADR-010). Policy and generated identities are
 * prepared once and reused for every writing attempt.
 *
 * <p>Monotonic elapsed time covers lookup, preparation, database work and backoff. Admission reserves
 * pool-acquisition and transaction allowances, while jitter runs outside transactions. The deadline
 * is cooperative and never interrupts cleanup. Instances contain per-request state and are not shared.
 */
final class OperationRecovery {
    // Admission reserves pool acquisition plus the existing five-second transaction timeout.
    private static final long ADMISSION_NANOS = 10_000_000_000L;
    private final OperationCoordinator coordinator;
    private final RetryPolicy policy;
    private final RecoveryRuntime runtime;
    private final SqlServerFailureClassifier classifier = new SqlServerFailureClassifier();
    private final long started;

    OperationRecovery(OperationCoordinator coordinator, RetryPolicy policy, RecoveryRuntime runtime) {
        this.coordinator = coordinator;
        this.policy = policy;
        this.runtime = runtime;
        this.started = runtime.nanoTime();
    }

    <A, T> T execute(Supplier<Optional<T>> lookup, Supplier<A> prepare, Function<A, T> write) {
        var existing = lookup(lookup, null, false);
        if (existing.isPresent()) { return existing.get(); }
        A prepared = prepare.get();
        for (int number = 1; number <= policy.maxAttempts(); number++) {
            admit(null);
            var completion = new AttemptCompletion();
            try {
                return coordinator.execute(() -> {
                    TransactionSynchronizationManager.registerSynchronization(completion);
                    return write.apply(prepared);
                });
            } catch (RuntimeException failure) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) { throw failure; }
                boolean duplicate = failure instanceof DuplicateOperation;
                boolean database = failure instanceof DataAccessException || failure instanceof TransactionException;
                if (!duplicate && !database) { throw failure; }
                // ADR-010: failed commit/cleanup never authorizes a new write, even after empty reads.
                if (!completion.rolledBack()) {
                    return lookup(lookup, failure, true).orElseThrow(() -> unavailable(failure));
                }
                if (!duplicate && !classifier.retryableRollback(failure)) { throw failure; }
                var recovered = lookup(lookup, failure, false);
                if (recovered.isPresent()) { return recovered.get(); }
                if (duplicate || number == policy.maxAttempts()) { throw unavailable(failure); }
                pause(number, failure);
            }
        }
        throw new IllegalStateException("Retry loop exhausted without an outcome");
    }

    private <T> Optional<T> lookup(Supplier<Optional<T>> lookup, RuntimeException original, boolean uncertain) {
        for (int number = 1; number <= policy.maxAttempts(); number++) {
            admit(original);
            try {
                var result = coordinator.execute(lookup);
                if (result.isPresent() || !uncertain) { return result; }
            } catch (DataAccessException | TransactionException failure) {
                if (original == null) { original = failure; }
                // Read recovery never authorizes reexecution until a successful committed lookup.
                if (!uncertain && !classifier.retryableRollback(failure)) { throw failure; }
            }
            if (number < policy.maxAttempts()) { pause(number, original); }
        }
        throw unavailable(original);
    }

    private void admit(RuntimeException cause) {
        long remaining = policy.budgetMillis() * 1_000_000L - (runtime.nanoTime() - started);
        if (Thread.currentThread().isInterrupted() || remaining < ADMISSION_NANOS) { throw unavailable(cause); }
    }

    private void pause(int number, RuntimeException cause) {
        long maximum = policy.backoffMillis() * (1L << (number - 1));
        long remaining = policy.budgetMillis() * 1_000_000L - (runtime.nanoTime() - started);
        if (remaining < ADMISSION_NANOS + maximum * 1_000_000L) { throw unavailable(cause); }
        try {
            runtime.pause(maximum);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable(interrupted);
        }
    }

    private OperationFailure unavailable(Throwable cause) {
        return new OperationFailure(OperationFailureReason.UNAVAILABLE, cause);
    }
}
