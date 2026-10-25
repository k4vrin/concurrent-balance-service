package dev.kavrin.balance.account.infrastructure.persistence;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.application.BalanceReader;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SQL Server account adapter for balance queries and financial participant locking.
 * Balance queries read only the synchronous projection; writers receive managed entities
 * after sequential locking reads in the shared application order (ADR-009 and ADR-010).
 * This adapter joins the caller's transaction and never commits account changes itself.
 */
@Repository
public class AccountStore implements BalanceReader {
    private static final int QUERY_TIMEOUT_MILLIS = 2000;
    private static final String FIND_BALANCE = """
            SELECT a.balance AS balance, a.currency AS currency
            FROM AccountEntity a
            WHERE a.id = :accountId
            """;
    private static final String LOCK_ACCOUNT = """
            SELECT account_id, category, currency, balance, status, created_at
            -- UPDLOCK takes update locks during this read and holds them until commit/rollback,
            -- serializing competing writers before they check funds and update the projection.
            -- HOLDLOCK applies SERIALIZABLE semantics to this table reference, protecting
            -- a missing account's key range against insertion until the transaction ends.
            FROM dbo.accounts WITH (UPDLOCK,HOLDLOCK)
            WHERE account_id = CAST(:accountId AS uniqueidentifier)
            """;

    private final EntityManager entities;

    public AccountStore(EntityManager entities) {
        this.entities = entities;
    }

    /**
     * Reads the stored amount and currency without loading a managed account or its history.
     * The caller supplies the read-committed transaction used by authoritative balance lookup;
     * this method does not establish an isolation level or acquire financial update locks.
     *
     * @param id account whose balance projection is requested
     * @return the stored balance, or empty when the account does not exist; zero is a valid balance
     */
    @Override
    public Optional<Money> findBalance(AccountId id) {
        var rows = entities.createQuery(FIND_BALANCE, Tuple.class)
                .setParameter("accountId", id.value())
                .setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLIS)
                .getResultList();
        return rows.stream().findFirst().map(BalanceProjection::from).map(BalanceProjection::toMoney);
    }

    /**
     * Locks each distinct participant sequentially in {@link AccountLockOrder#DESCENDING} order.
     * SQL Server update/serializable locking hints request protection until transaction completion,
     * including a missing-key range. Missing accounts are omitted so the use case can decide
     * whether to retain a business rejection. Existing accounts are refreshed after locking to
     * replace any previously managed snapshot; call this before changing managed account state.
     *
     * @param participants complete participant set for the financial attempt
     * @return an unmodifiable map in lock-acquisition order, containing managed account entities
     *         whose projections may be changed inside the owning transaction
     * @throws IllegalStateException if no writable, joined transaction is active; Spring repository
     *         exception translation may wrap this exception for proxied callers
     */
    public Map<AccountId, AccountEntity> lockAll(Collection<AccountId> participants) {
        requireWritableTransaction();
        var lockedAccounts = new LinkedHashMap<AccountId, AccountEntity>();
        // ADR-010: sequential point reads follow application order, including missing-key range protection.
        for (var id : participants.stream().distinct().sorted(AccountLockOrder.DESCENDING).toList()) {
            lockAccount(id).ifPresent(account -> lockedAccounts.put(id, account));
        }
        return Collections.unmodifiableMap(lockedAccounts);
    }

    /** Refreshes only after the locking query, so a cached entity cannot supply a stale funds check. */
    private Optional<AccountEntity> lockAccount(AccountId id) {
        List<?> rows = entities.createNativeQuery(LOCK_ACCOUNT, AccountEntity.class)
                .setParameter("accountId", id.value().toString())
                .setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLIS)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var account = AccountEntity.class.cast(rows.getFirst());
        entities.refresh(account);
        return Optional.of(account);
    }

    private void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !entities.isJoinedToTransaction()) {
            throw new IllegalStateException("Account locks require the owning financial transaction");
        }
    }

    /** Keeps JPQL column aliases and types at the persistence boundary instead of using array positions. */
    private record BalanceProjection(long amount, String currency) {
        static BalanceProjection from(Tuple row) {
            return new BalanceProjection(row.get("balance", Long.class), row.get("currency", String.class));
        }

        Money toMoney() {
            return new Money(amount, Currency.getInstance(currency));
        }
    }
}
