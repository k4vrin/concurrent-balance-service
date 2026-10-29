package dev.kavrin.balance.account.application;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import java.util.Optional;

/**
 * Reads the authoritative committed account projection through an application port.
 *
 * <p>Absence means the account does not exist, while a present zero balance is valid. The caller
 * owns the read transaction; this port does not create operations or reconstruct ledger history.
 */
public interface BalanceReader {
    Optional<Money> findBalance(AccountId id);
}
