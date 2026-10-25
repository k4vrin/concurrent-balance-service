package dev.kavrin.balance.operation.application;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class OperationCoordinator {
    private final TransactionTemplate transaction;

    public OperationCoordinator(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    public <T> T execute(Supplier<T> attempt) {
        // ADR-010: joining a caller's transaction could return success before that caller commits.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation coordinator must own its transaction");
        }
        return transaction.execute(status -> attempt.get());
    }
}
