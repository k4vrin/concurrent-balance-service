package dev.kavrin.balance.operation.application;

import java.util.Objects;

import dev.kavrin.balance.money.Money;

final class CommandValidation {

    private CommandValidation() {
    }

    static void requirePositiveAmount(Money amount) {
        Objects.requireNonNull(amount, "Amount is required");
        if (amount.amount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
