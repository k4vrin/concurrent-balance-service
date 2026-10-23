package dev.kavrin.balance.operation.application;

import java.util.Objects;
import dev.kavrin.balance.account.domain.AccountId;

/** Retain this selection with execution metadata; replay must not rerun current funding policy. */
public record FundingSelection(AccountId counterpartAccountId, String policyVersion) {
    public FundingSelection {
        Objects.requireNonNull(counterpartAccountId, "Funding counterpart is required");
        CanonicalFingerprint.requireIdentity(policyVersion);
    }
}
