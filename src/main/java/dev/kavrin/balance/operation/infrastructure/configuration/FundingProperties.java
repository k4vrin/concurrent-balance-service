package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
import java.util.UUID;

public record FundingProperties(UUID accountId, String policyVersion) {
    FundingSelection toSelection() {
        return new FundingSelection(new AccountId(accountId), policyVersion);
    }
}
