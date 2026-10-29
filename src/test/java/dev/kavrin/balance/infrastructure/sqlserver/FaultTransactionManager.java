package dev.kavrin.balance.infrastructure.sqlserver;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

/**
 * Delegates real transactions and injects one selected acknowledgement/rollback failure for tests.
 *
 * <p>BEFORE_COMMIT rolls back real writes instead of committing; other failures follow a real
 * delegate commit. Recovery must never return the callback candidate without confirming the
 * retained result. The wrapper does not interrupt a physical database connection during commit.
 */
final class FaultTransactionManager implements PlatformTransactionManager {
    private final PlatformTransactionManager delegate;
    private final ApplicationFaultPlan plan;

    FaultTransactionManager(PlatformTransactionManager delegate, ApplicationFaultPlan plan) { this.delegate = delegate; this.plan = plan; }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) { return delegate.getTransaction(definition); }

    @Override
    public void commit(TransactionStatus status) {
        var fault = plan.nextCommit();
        if (fault == CommitFaultMode.BEFORE_COMMIT) {
            delegate.rollback(status);
            throw new TransactionSystemException("injected failure before commit");
        }
        delegate.commit(status);
        if (fault == CommitFaultMode.HIDE_COMMITTED_RESULT) { plan.hideCommittedReads(); }
        if (fault != CommitFaultMode.NONE) { throw new TransactionSystemException("injected lost commit acknowledgement"); }
    }

    @Override
    public void rollback(TransactionStatus status) { delegate.rollback(status); }
}
