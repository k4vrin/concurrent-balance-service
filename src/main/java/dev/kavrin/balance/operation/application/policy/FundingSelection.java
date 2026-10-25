package dev.kavrin.balance.operation.application.policy;

import dev.kavrin.balance.account.domain.AccountId;
import java.util.Objects;

/** Retain this selection with execution metadata; replay must not rerun current funding policy. */
public record FundingSelection(AccountId counterpartAccountId, String policyVersion) {
    public FundingSelection {
        Objects.requireNonNull(counterpartAccountId, "Funding counterpart is required");
        IdentityText.requireValid(policyVersion);
    }
}
