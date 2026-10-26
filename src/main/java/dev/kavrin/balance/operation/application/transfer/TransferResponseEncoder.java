package dev.kavrin.balance.operation.application.transfer;

import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.StoredResponse;

/**
 * Application port for producing and checking the HTTP response retained with a transfer outcome.
 * Keeps JSON serialization outside the handler while allowing the outcome and its response to
 * be persisted in the same transaction. Implementations must reject unusable retained responses.
 */
public interface TransferResponseEncoder {
    /**
     * Serializes a transfer outcome before the store marks the operation complete.
     * Encoding failures must propagate so the owning transaction cannot commit incomplete work.
     *
     * @param command original transfer intent and retry identity
     * @param result terminal outcome and currency to encode
     * @return the response envelope and serialized body to retain
     */
    StoredResponse encode(TransferCommand command, MutationResult result);

    /**
     * Checks that a replay response is readable and agrees with its retained outcome, identity,
     * operation type, currency and status. Validation must not regenerate or replace the response.
     *
     * @param result original outcome with a retained response present
     * @throws IllegalStateException if the decoded response contradicts the retained outcome
     */
    void validate(MutationResult result);
}
