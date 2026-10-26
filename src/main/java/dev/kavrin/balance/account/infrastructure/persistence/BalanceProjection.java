package dev.kavrin.balance.account.infrastructure.persistence;

import dev.kavrin.balance.money.Money;
import jakarta.persistence.Tuple;
import java.util.Currency;

record BalanceProjection(long amount, String currency) {
    static BalanceProjection from(Tuple row) {
        return new BalanceProjection(row.get("balance", Long.class), row.get("currency", String.class));
    }

    Money toMoney() {
        return new Money(amount, Currency.getInstance(currency));
    }
}
