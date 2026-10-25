package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.Objects;
import java.util.Optional;
import dev.kavrin.balance.operation.domain.OperationOutcome;

/** Original outcome/currency, optionally accompanied by the serialized response retained at commit. */
public record MutationResult(OperationOutcome outcome, Currency currency, Optional<StoredResponse> storedResponse) {
    public MutationResult(OperationOutcome outcome, Currency currency) {
        this(outcome, currency, Optional.empty());
    }

    public MutationResult {
        Objects.requireNonNull(outcome, "Outcome is required");
        Objects.requireNonNull(currency, "Currency is required");
        Objects.requireNonNull(storedResponse, "Retained response presence must be explicit");
    }
}
