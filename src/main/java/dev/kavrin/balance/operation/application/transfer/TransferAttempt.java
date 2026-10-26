package dev.kavrin.balance.operation.application.transfer;

import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.identity.CanonicalRequest;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import java.util.UUID;

/** One operation identity and retained policy snapshot for a new transfer's transaction. */
public record TransferAttempt(
    UUID operationId,
    UUID journalId,
    TransferCommand command,
    CanonicalRequest canonical,
    ExecutionPolicy policy
) {
}
