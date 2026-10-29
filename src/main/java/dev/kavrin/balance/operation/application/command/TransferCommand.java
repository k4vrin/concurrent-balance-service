package dev.kavrin.balance.operation.application.command;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.util.Objects;

/**
 * Captures validated immutable transfer intent independently of HTTP and persistence.
 *
 * <p>Both accounts are required and must differ, and the amount must be positive. Construction does
 * not reserve identity or determine available funds; replay and locked business checks occur later.
 *
 * @param transactionId caller-supplied retry identity
 * @param sourceAccountId customer account providing funds
 * @param destinationAccountId distinct customer account receiving funds
 * @param amount positive monetary amount with its requested currency
 */
public record TransferCommand(TransactionId transactionId, AccountId sourceAccountId,
        AccountId destinationAccountId, Money amount) {

    public TransferCommand {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(sourceAccountId, "Source account ID is required");
        Objects.requireNonNull(destinationAccountId, "Destination account ID is required");
        CommandValidation.requirePositiveAmount(amount);
        // Reject invalid intent before reserving an operation, not as a terminal business outcome.
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Transfer accounts must be distinct");
        }
    }
}
