package dev.kavrin.balance.operation.application.execution;

/** Only the named global transaction-ID key permits fresh committed-result recovery. */
public final class DuplicateOperation extends RuntimeException {
    public DuplicateOperation(Throwable cause) { super("Operation identity is already reserved", cause); }
}
