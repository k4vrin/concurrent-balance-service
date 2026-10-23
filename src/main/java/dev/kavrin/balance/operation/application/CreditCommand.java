package dev.kavrin.balance.operation.application;

import java.util.Objects;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;

public record CreditCommand(TransactionId transactionId, AccountId accountId, Money amount) {

    public CreditCommand {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(accountId, "Account ID is required");
        CommandValidation.requirePositiveAmount(amount);
    }
}
