package dev.kavrin.balance.operation.application.credit;

import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.identity.CanonicalRequest;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
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
