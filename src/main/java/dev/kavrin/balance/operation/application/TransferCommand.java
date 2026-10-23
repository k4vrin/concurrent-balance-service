package dev.kavrin.balance.operation.application;

import java.util.Objects;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;

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
