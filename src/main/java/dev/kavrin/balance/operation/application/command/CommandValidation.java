package dev.kavrin.balance.operation.application.command;

import dev.kavrin.balance.money.Money;
import java.util.Objects;

/**
 * Applies the shared positive-amount requirement before mutation execution.
 *
 * <p>Command shape validation does not select supported currencies, evaluate policy caps or reserve
 * transaction identity. Those decisions belong to the replay-aware application flow.
 */
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
