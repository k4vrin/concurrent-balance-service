package dev.kavrin.balance.infrastructure.sqlserver;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

/**
 * Simulates loss of the second commit acknowledgement after delegating the actual database commit.
 *
 * <p>The first completion belongs to the initial retained-result lookup; the second belongs to
 * the writing attempt in the intended fixture flow. Throwing only after that commit lets tests
 * verify recovery of durable effects without reexecuting the mutation.
 *
 * <p>All transaction operations delegate unchanged otherwise. This wrapper simulates an application
 * acknowledgement failure, not a driver disconnect or network partition.
 */
final class LostCommitAcknowledgementManager implements PlatformTransactionManager {
    private final PlatformTransactionManager delegate;
    private final AtomicInteger commits = new AtomicInteger();

    LostCommitAcknowledgementManager(PlatformTransactionManager delegate) { this.delegate = delegate; }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) { return delegate.getTransaction(definition); }

    @Override
    public void commit(TransactionStatus status) {
        delegate.commit(status);
        if (commits.incrementAndGet() == 2) { throw new TransactionSystemException("simulated lost commit acknowledgement"); }
    }

    @Override
    public void rollback(TransactionStatus status) { delegate.rollback(status); }
}
