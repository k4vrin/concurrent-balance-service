package dev.kavrin.balance.operation.application.debit;

import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.identity.CanonicalRequest;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
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
