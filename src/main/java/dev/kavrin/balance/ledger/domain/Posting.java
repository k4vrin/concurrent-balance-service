package dev.kavrin.balance.ledger.domain;

import java.util.Objects;

import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;

/**
 * Represents one immutable, positive monetary line assigned to an account and accounting side.
 *
 * <p>The account category determines whether the line increases or decreases its projection.
 * A posting alone does not prove a journal is balanced or persist any financial effect (ADR-007).
 *
 * @param accountId account receiving the line
 * @param side debit or credit side, interpreted with the account category
 * @param amount strictly positive amount and currency
 */
public record Posting(AccountId accountId, PostingSide side, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "Posting account ID is required");
        Objects.requireNonNull(side, "Posting side is required");
        Objects.requireNonNull(amount, "Posting amount is required");
        if (amount.amount() <= 0) {
            throw new IllegalArgumentException("Posting amount must be positive");
        }
    }

    public Money balanceDelta(AccountCategory category) {
        Objects.requireNonNull(category, "Account category is required");

        // ADR-007: a credit increases customer liabilities; a debit increases
        // debit-normal funding balances. Accounting sides are not universal signs.
        boolean increasesBalance = switch (category) {
            case CUSTOMER_LIABILITY -> side == PostingSide.CREDIT;
            case FUNDING_CLEARING -> side == PostingSide.DEBIT;
        };
        return increasesBalance ? amount : new Money(-amount.amount(), amount.currency());
    }
}
