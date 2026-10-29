package dev.kavrin.balance.operation.application.execution;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Records Spring transaction lifecycle decisions for portable orchestration tests.
 *
 * <p>Each begin, commit and rollback appends an event. Configurable commit and rollback failures
 * exercise uncertainty and cleanup paths through Spring's synchronization machinery. The manager
 * has no database, locks or durable state and cannot prove physical transaction outcomes.
 *
 * <p>Mutable event and failure state belongs to one test instance; concurrent use is unsupported.
 */
public final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    private final List<String> events = new ArrayList<>();
    private boolean failRollback;
    private int commits;
    private int failingCommit;
    private TransactionSystemException commitFailure;

    public void failRollback() { failRollback = true; }
    public List<String> events() { return events; }
    public void failCommitOn(int number) { failingCommit = number; }
    public void failCommit(TransactionSystemException failure) { commitFailure = failure; }
    public TransactionSystemException commitFailure() { return commitFailure; }

    @Override
    protected Object doGetTransaction() { return new Object(); }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        events.add("commit");
        if (commitFailure != null) { throw commitFailure; }
        if (++commits == failingCommit) { throw new TransactionSystemException("commit outcome uncertain"); }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        events.add("rollback");
        if (failRollback) { throw new TransactionSystemException("rollback completion unknown"); }
    }
}
