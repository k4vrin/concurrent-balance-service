package dev.kavrin.balance.operation.domain;

/** A terminal business result value; constructing it does not prove database commit. */
public sealed interface OperationOutcome permits OperationSuccess, OperationRejected {

    TransactionId transactionId();

    OperationType operationType();

}
