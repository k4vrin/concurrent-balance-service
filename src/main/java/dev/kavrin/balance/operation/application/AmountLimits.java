package dev.kavrin.balance.operation.application;

/** Business limits for a mutation and a customer projection; funding keeps its signed-long rules. */
public record AmountLimits(long maxTransactionAmount, long maxCustomerBalance) {
    public AmountLimits {
        if (maxTransactionAmount <= 0 || maxCustomerBalance <= 0) {
            throw new IllegalArgumentException("Transaction and customer balance limits must be positive");
        }
    }

    public boolean acceptsTransactionAmount(long amount) {
        return amount > 0 && amount <= maxTransactionAmount;
    }

    public boolean acceptsCustomerBalance(long balance) {
        return balance >= 0 && balance <= maxCustomerBalance;
    }
}
