package dev.kavrin.balance.operation.application;

import java.util.Objects;

/** Nonterminal failures, separate from business rejections that must commit as values. */
public final class OperationFailure extends RuntimeException {
    public enum Reason { ACCOUNT_NOT_FOUND, TRANSACTION_ID_CONFLICT, UNSUPPORTED_CURRENCY, UNAVAILABLE }

    private final Reason reason;

    public OperationFailure(Reason reason) {
        this(reason, null);
    }

    public OperationFailure(Reason reason, Throwable cause) {
        super(Objects.requireNonNull(reason).name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
