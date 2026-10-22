package dev.kavrin.balance.operation.application;

/**
 * Financial operation contract for the first implementation milestone.
 * Amounts use a documented minor currency unit. Transaction IDs identify an
 * operation across retries and across all three mutation types.
 * Successful mutations must commit a balanced journal, immutable postings,
 * account balance projections and the operation outcome in one transaction.
 * Replays return the original outcome without new postings; business rejections
 * have no journal. Balance reads use committed synchronous projections.
 * No implementation is registered during project bootstrap. See docs/en/Ledger.md.
 */
public interface BalanceService {

    void credit(String accountId, long amount, String transactionId);

    void debit(String accountId, long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId,
                  long amount, String transactionId);

    long getBalance(String accountId);
}
