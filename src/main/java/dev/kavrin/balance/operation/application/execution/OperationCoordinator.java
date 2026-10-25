package dev.kavrin.balance.operation.application.execution;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the Spring transaction boundary around an operation attempt or replay lookup.
 * Refuses an existing caller transaction so a result cannot escape before that caller commits
 * (ADR-010). Each invocation executes once; this class does not implement a retry loop.
 */
@Service
public final class OperationCoordinator {
    private final TransactionTemplate transaction;

    /**
     * Configures read-committed transactions with a five-second timeout.
     *
     * @param manager transaction manager responsible for begin, commit and rollback
     */
    public OperationCoordinator(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
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
