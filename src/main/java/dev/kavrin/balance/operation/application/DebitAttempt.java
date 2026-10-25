package dev.kavrin.balance.operation.application;

import java.util.UUID;

/** One operation identity and retained server selection for a new debit's transaction. */
public record DebitAttempt(
    UUID operationId,
    UUID journalId,
    DebitCommand command,
    CanonicalRequest canonical,
    ExecutionPolicy policy,
    FundingSelection funding
) {
}
