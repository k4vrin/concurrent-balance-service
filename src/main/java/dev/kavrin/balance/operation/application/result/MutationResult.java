package dev.kavrin.balance.operation.application.result;

import dev.kavrin.balance.operation.domain.OperationOutcome;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

/**
 * Carries an operation's original success or terminal rejection to the HTTP adapter, including
 * the original currency and, when available, its retained response. Replay uses these retained
 * values rather than reevaluating current policy. Construction alone does not prove a commit;
 * the application transaction boundary controls when this value may leave the handler.
 *
 * @param outcome original terminal operation outcome
 * @param currency currency associated with that outcome
 * @param storedResponse retained response, or empty while preparing a response for persistence
 */
public record MutationResult(OperationOutcome outcome, Currency currency, Optional<StoredResponse> storedResponse) {
    /**
     * Creates a result without a retained response, for example before response encoding.
     *
     * @param outcome terminal operation outcome
     * @param currency operation currency
     * @throws NullPointerException if either argument is null
     */
    public MutationResult(OperationOutcome outcome, Currency currency) {
        this(outcome, currency, Optional.empty());
    }

    /**
     * Requires explicit non-null outcome, currency and response presence.
     *
     * @throws NullPointerException if any component is null; use {@link Optional#empty()}
     *         to represent an absent response
     */
    public MutationResult {
        Objects.requireNonNull(outcome, "Outcome is required");
        Objects.requireNonNull(currency, "Currency is required");
        Objects.requireNonNull(storedResponse, "Retained response presence must be explicit");
    }
}
