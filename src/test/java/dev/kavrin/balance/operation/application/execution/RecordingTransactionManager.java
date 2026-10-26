package dev.kavrin.balance.operation.application.execution;

import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Records Spring lifecycle decisions only; it does not simulate database locks or durability. */
public final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    private final List<String> events = new ArrayList<>();
    private int commits;
    private int failingCommit;
    private TransactionSystemException commitFailure;

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
    protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
}
