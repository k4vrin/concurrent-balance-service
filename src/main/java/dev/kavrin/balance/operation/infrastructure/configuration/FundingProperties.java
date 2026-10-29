package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
import java.util.UUID;

/**
 * Binds a currency's externally configured funding identity and retained policy version.
 *
 * <p>Conversion creates the validated domain selection. Account existence, category, status and
 * currency compatibility are checked under the financial lock protocol.
 *
 * @param accountId configured funding counterpart UUID
 * @param policyVersion version retained with the selected counterpart
 */
public record FundingProperties(UUID accountId, String policyVersion) {
    FundingSelection toSelection() {
        return new FundingSelection(new AccountId(accountId), policyVersion);
    }
}
