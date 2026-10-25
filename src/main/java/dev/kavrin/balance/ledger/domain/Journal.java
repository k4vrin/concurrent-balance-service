package dev.kavrin.balance.ledger.domain;

import java.math.BigInteger;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;

/**
 * Immutable, balanced collection of accounting postings in one currency.
 * Each {@link Posting} names an account, a debit/credit side and a positive amount.
 * Construction checks the posting set; {@link #projectBalances(Map)} then calculates its
 * combined effect on supplied account snapshots using each account's category.
 *
 * <p>For example, debiting a customer by 100 and crediting another customer by 100
 * projects balances of 500 and 200 to 400 and 300. Debit/credit signs depend on account
 * category: a debit decreases a customer liability but increases a funding/clearing balance.
 *
 * <p>This is an in-memory domain value, not a persisted journal header. It does not assign
 * journal/operation IDs, acquire locks, write balances or commit a transaction. The application
 * and persistence layers own those responsibilities under ADR-007. Deployment-specific
 * currency support, transaction limits and customer-balance caps are checked outside this record.
 *
 * @param postings complete posting set, defensively copied into an unmodifiable list;
 *                 input order is retained by {@link #postings()} and participates in record equality
 */
public record Journal(List<Posting> postings) {

    /**
     * Validates and copies the complete journal before creating the record.
     * Requires at least two non-null lines, a single currency and equal total debits and credits.
     * Totals use {@link BigInteger}, so several valid {@code long} amounts cannot overflow
     * and make an unbalanced journal appear balanced. Account existence and resulting balances
     * are checked later when projecting against account snapshots.
     *
     * @param postings accounting lines to validate and retain
     * @throws NullPointerException if the list or any posting is null
     * @throws IllegalArgumentException if there are fewer than two postings, currencies differ,
     *         or debit and credit totals do not match
     */
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
     * Calculates proposed account balances for the complete journal without changing inputs.
     * Copies the supplied map, resolves every posting's account, sums category-dependent signed
     * changes per account, then constructs all proposed snapshots. Only final balances are
     * checked: temporary negative or overflowing line-by-line balances are not applied.
     *
     * <p>The result includes posting participants only, including those whose net change is zero.
     * Extra supplied accounts are omitted. No partial result is returned if any participant fails.
     * Map iteration order is unspecified and must not be used as a lock-acquisition order.
     *
     * <p>The caller must supply current accounts under the financial transaction's locking policy.
     * Copying the map does not acquire locks or create a database snapshot, and calculating the
     * result does not persist it or establish database atomicity.
     *
     * @param accounts account snapshots keyed by their own IDs; must contain every posting participant
     * @return an unmodifiable map of proposed participant snapshots after all checks succeed
     * @throws NullPointerException if the input map, any key or any value is null
     * @throws IllegalArgumentException if a posting account is missing, its ID or currency does
     *         not match, or its final balance violates its category rules, such as customer nonnegativity
     * @throws ArithmeticException if any final balance is outside the signed {@code long} range
     */
    public Map<AccountId, Account> projectBalances(Map<AccountId, Account> accounts) {
        Map<AccountId, Account> snapshots = Map.copyOf(accounts);
        Map<AccountId, BigInteger> netChanges = calculateNetChanges(snapshots);
        return projectAccounts(snapshots, netChanges);
    }

    /**
     * Resolves each posting and accumulates its signed effect by account ID.
     * {@link Posting#balanceDelta(dev.kavrin.balance.account.domain.AccountCategory)} determines
     * the sign from the resolved category; repeated postings for one account are added together.
     * The accumulator uses {@link BigInteger} because a net change can exceed {@code long}
     * even when the old and final balances both fit.
     *
     * @param accounts copied account snapshots used to resolve posting participants
     * @return a temporary map of exact net changes, including zero changes for participating accounts
     * @throws IllegalArgumentException if any posting cannot be resolved to a compatible account
     */
    private Map<AccountId, BigInteger> calculateNetChanges(Map<AccountId, Account> accounts) {
        Map<AccountId, BigInteger> netChanges = new HashMap<>();
        for (Posting posting : postings) {
            Account account = resolvePostingAccount(posting, accounts);
            Money delta = posting.balanceDelta(account.category());
            netChanges.merge(account.id(), BigInteger.valueOf(delta.amount()), BigInteger::add);
        }
        return netChanges;
    }

    /**
     * Finds the supplied snapshot for a posting and verifies its identity and currency.
     * This lookup reads the provided map only; it does not query storage, check active status
     * or decide whether the account category is permitted for a particular public operation.
     *
     * @param posting accounting line whose account is needed
     * @param accounts snapshots keyed by account ID
     * @return the matching account snapshot
     * @throws IllegalArgumentException if the account is absent, the map key disagrees with its
     *         ID, or the account and posting use different currencies
     */
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

    /**
     * Builds proposed snapshots for every account with an accumulated posting effect.
     * Keeps intermediate results local and exposes an unmodifiable map only after every
     * participant passes its final-balance checks. This all-or-fail calculation is separate
     * from the database transaction required to persist the proposals together.
     *
     * @param accounts previously resolved original snapshots
     * @param netChanges exact per-account changes calculated from this journal
     * @return an unmodifiable map containing only the journal's participants
     * @throws ArithmeticException if a final balance cannot be represented as a {@code long}
     * @throws IllegalArgumentException if a final balance violates its account category rules
     */
    private Map<AccountId, Account> projectAccounts(Map<AccountId, Account> accounts,
            Map<AccountId, BigInteger> netChanges) {
        Map<AccountId, Account> projections = new HashMap<>();
        netChanges.forEach((accountId, netChange) ->
                projections.put(accountId, projectAccount(accounts.get(accountId), netChange)));

        // No proposed state escapes until every participant passes its balance policy.
        return Map.copyOf(projections);
    }

    /**
     * Adds one account's complete net change to its original balance using exact arithmetic.
     * Converts only the final sum to {@code long}, then constructs a new {@link Account} to
     * enforce category rules. Preserves the original account ID, category and currency.
     *
     * @param account original immutable snapshot
     * @param netChange combined signed effect of all this account's postings, possibly beyond {@code long}
     * @return a new snapshot with the validated final balance
     * @throws ArithmeticException if the final sum is outside the signed {@code long} range
     * @throws IllegalArgumentException if the resulting balance violates category rules
     */
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
