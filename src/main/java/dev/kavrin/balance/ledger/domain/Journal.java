package dev.kavrin.balance.ledger.domain;

import java.math.BigInteger;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;

/** A validated single-currency posting set; persisted identity and operation linkage come later. */
public record Journal(List<Posting> postings) {

    public Journal {
        postings = List.copyOf(postings);
        if (postings.size() < 2) {
            throw new IllegalArgumentException("Journal requires at least two postings");
        }

        Currency currency = postings.getFirst().amount().currency();
        BigInteger debits = BigInteger.ZERO;
        BigInteger credits = BigInteger.ZERO;
        for (Posting posting : postings) {
            if (!currency.equals(posting.amount().currency())) {
                throw new IllegalArgumentException("Journal postings must use the same currency");
            }

            // Several valid long amounts can exceed long in aggregate. Exact totals
            // prevent overflow from making an unbalanced journal appear balanced.
            BigInteger amount = BigInteger.valueOf(posting.amount().amount());
            switch (posting.side()) {
                case DEBIT -> debits = debits.add(amount);
                case CREDIT -> credits = credits.add(amount);
            }
        }

        if (!debits.equals(credits)) {
            throw new IllegalArgumentException("Journal debit and credit totals must match");
        }
    }

    /**
     * Returns immutable proposed snapshots for posting participants only. The caller
     * must supply current accounts under the financial transaction's locking policy;
     * this calculation neither acquires locks nor persists the returned balances.
     */
    public Map<AccountId, Account> projectBalances(Map<AccountId, Account> accounts) {
        Map<AccountId, Account> snapshots = Map.copyOf(accounts);
        Map<AccountId, BigInteger> netChanges = calculateNetChanges(snapshots);
        return projectAccounts(snapshots, netChanges);
    }

    private Map<AccountId, BigInteger> calculateNetChanges(Map<AccountId, Account> accounts) {
        Map<AccountId, BigInteger> netChanges = new HashMap<>();
        for (Posting posting : postings) {
            Account account = resolvePostingAccount(posting, accounts);
            Money delta = posting.balanceDelta(account.category());
            netChanges.merge(account.id(), BigInteger.valueOf(delta.amount()), BigInteger::add);
        }
        return netChanges;
    }

    private Account resolvePostingAccount(Posting posting, Map<AccountId, Account> accounts) {
        Account account = accounts.get(posting.accountId());
        if (account == null) {
            throw new IllegalArgumentException("Posting account is missing");
        }
        if (!posting.accountId().equals(account.id())) {
            throw new IllegalArgumentException("Account map key must match account ID");
        }
        if (!posting.amount().currency().equals(account.balance().currency())) {
            throw new IllegalArgumentException("Posting and account currencies must match");
        }
        return account;
    }

    private Map<AccountId, Account> projectAccounts(Map<AccountId, Account> accounts,
            Map<AccountId, BigInteger> netChanges) {
        Map<AccountId, Account> projections = new HashMap<>();
        netChanges.forEach((accountId, netChange) ->
                projections.put(accountId, projectAccount(accounts.get(accountId), netChange)));

        // No proposed state escapes until every participant passes its balance policy.
        return Map.copyOf(projections);
    }

    private Account projectAccount(Account account, BigInteger netChange) {
        // ADR-007: validate the complete journal effect, not temporary line-by-line
        // states. Even a representable final balance can require a delta beyond long.
        Money currentBalance = account.balance();
        long finalAmount = BigInteger.valueOf(currentBalance.amount())
                .add(netChange).longValueExact();
        Money finalBalance = new Money(finalAmount, currentBalance.currency());
        return new Account(account.id(), account.category(), finalBalance);
    }
}
