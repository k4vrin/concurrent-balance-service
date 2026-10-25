package dev.kavrin.balance.operation.application.command;

import dev.kavrin.balance.money.Money;
import java.util.Objects;

public final class CommandValidation {

    private CommandValidation() {
    }

    public static void requirePositiveAmount(Money amount) {
        Objects.requireNonNull(amount, "Amount is required");
        if (amount.amount() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
