package dev.kavrin.balance.account.application;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import java.util.Optional;

public interface BalanceReader {
    Optional<Money> findBalance(AccountId id);
}
