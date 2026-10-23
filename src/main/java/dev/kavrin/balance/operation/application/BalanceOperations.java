package dev.kavrin.balance.operation.application;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;

/** HTTP-facing application boundary. Mutation results may leave it only after confirmed commit. */
public interface BalanceOperations {
    MutationResult credit(CreditCommand command);
    MutationResult debit(DebitCommand command);
    MutationResult transfer(TransferCommand command);
    Money balance(AccountId accountId);
}
