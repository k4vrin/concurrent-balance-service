package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.Objects;
import dev.kavrin.balance.operation.domain.OperationOutcome;

/** Original outcome and currency, retained together for replay by the future durable coordinator. */
public record MutationResult(OperationOutcome outcome, Currency currency) {
    public MutationResult {
        Objects.requireNonNull(outcome, "Outcome is required");
        Objects.requireNonNull(currency, "Currency is required");
    }
}
