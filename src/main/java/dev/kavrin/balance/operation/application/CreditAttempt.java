package dev.kavrin.balance.operation.application;

import java.util.UUID;

/** One operation identity and retained server selection for a new credit's transaction. */
public record CreditAttempt(
    UUID operationId,
    UUID journalId,
    CreditCommand command,
    CanonicalRequest canonical,
    ExecutionPolicy policy,
    FundingSelection funding
) {
}
