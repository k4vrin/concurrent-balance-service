package dev.kavrin.balance.operation.application.result;

import java.util.Objects;

/** Nonterminal failures, separate from business rejections that must commit as values. */
public final class OperationFailure extends RuntimeException {

    private final OperationFailureReason reason;

    public OperationFailure(OperationFailureReason reason) {
        this(reason, null);
    }

    public OperationFailure(OperationFailureReason reason, Throwable cause) {
        super(Objects.requireNonNull(reason).name(), cause);
        this.reason = reason;
    }

    public OperationFailureReason reason() {
        return reason;
    }
}
