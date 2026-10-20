package dev.kavrin.balance.operation.application;

/**
 * Financial operation contract for the first implementation milestone.
 * Amounts use a documented minor currency unit. Transaction IDs identify an
 * operation across retries and across all three mutation types.
 * No implementation is registered during project bootstrap.
 */
public interface BalanceService {

    void credit(String accountId, long amount, String transactionId);

    void debit(String accountId, long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId,
                  long amount, String transactionId);

    long getBalance(String accountId);
}
