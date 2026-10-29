package dev.kavrin.balance.operation.application.execution;

import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Records Spring's completion status for one writing attempt.
 *
 * <p>The status starts unknown and changes only through the transaction synchronization callback.
 * Only a reported rollback permits the recovery layer to consider another write; missing or
 * unknown completion must not be inferred from the original exception. Each instance belongs to
 * one attempt on the executing transaction thread.
 */
final class AttemptCompletion implements TransactionSynchronization {
    private int status = STATUS_UNKNOWN;

    @Override
    public void afterCompletion(int status) { this.status = status; }

    boolean rolledBack() { return status == STATUS_ROLLED_BACK; }
}
