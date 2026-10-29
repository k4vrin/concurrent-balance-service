package dev.kavrin.balance.operation.application.execution;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the Spring transaction boundary for financial attempts and retained-result lookups.
 *
 * <p>Ambient transactions are rejected so a callback result cannot escape before an outer caller's
 * commit. Each {@link #execute(Supplier)} invocation uses an owned, writable READ_COMMITTED
 * transaction with a five-second timeout and returns only after successful completion (ADR-010).
 *
 * <p>{@link #executeRecovering(Supplier, Supplier, Function)} surrounds complete mutation attempts
 * with bounded recovery. It prepares identities and policy once, retries eligible failures only
 * after reported rollback, and restricts uncertain completion to retained-result lookups. The
 * coordinator keeps no mutable per-request recovery state.
 */
@Service
@EnableConfigurationProperties(RetryPolicy.class)
public final class OperationCoordinator {
    private final TransactionTemplate transaction;
    private final RetryPolicy retryPolicy;
    private final RecoveryRuntime runtime;

    /**
     * Configures read-committed transactions with a five-second timeout.
     *
     * @param manager transaction manager responsible for begin, commit and rollback
     */
    public OperationCoordinator(PlatformTransactionManager manager) {
        this(manager, RetryPolicy.defaults(), new SystemRecoveryRuntime());
    }

    @Autowired
    public OperationCoordinator(PlatformTransactionManager manager, RetryPolicy retryPolicy) {
        this(manager, retryPolicy, new SystemRecoveryRuntime());
    }

    public OperationCoordinator(PlatformTransactionManager manager, RetryPolicy retryPolicy, RecoveryRuntime runtime) {
        this.retryPolicy = retryPolicy;
        this.runtime = runtime;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    /** Checks retained intent first and prepares identities/policy once for every writing retry. */
    public <A, T> T executeRecovering(Supplier<Optional<T>> lookup, Supplier<A> prepare, Function<A, T> write) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation coordinator must own its transaction");
        }
        return new OperationRecovery(this, retryPolicy, runtime).execute(lookup, prepare, write);
    }

    /**
     * Runs one callback in an owned transaction and returns only after successful transaction
     * completion. Callback failures propagate through Spring's rollback handling; transaction
     * failures propagate to the caller for classification or recovery.
     *
     * @param attempt database work to execute within the transaction
     * @param <T> callback result type
     * @return the callback result after successful transaction completion
     * @throws IllegalStateException if a caller transaction is already active
     * @throws org.springframework.transaction.TransactionException if transaction processing fails
     */
    public <T> T execute(Supplier<T> attempt) {
        // ADR-010: joining a caller's transaction could return success before that caller commits.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation coordinator must own its transaction");
        }
        return transaction.execute(status -> attempt.get());
    }
}
