package dev.kavrin.balance.account.infrastructure.persistence;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.application.BalanceReader;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("!smoke")
public class AccountStore implements BalanceReader {
    private final EntityManager entities;

    public AccountStore(EntityManager entities) { this.entities = entities; }

    @Override
    public Optional<Money> findBalance(AccountId id) {
        // CQRS: the committed query reads only the projection, never a writable aggregate or posting history.
        var rows = entities.createQuery("select a.balance, a.currency from AccountEntity a where a.id = :id", Object[].class)
                .setParameter("id", id.value()).setHint("jakarta.persistence.query.timeout", 2000).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(
                new Money((Long) rows.getFirst()[0], Currency.getInstance((String) rows.getFirst()[1])));
    }

    public Map<AccountId, AccountEntity> lockAll(Collection<AccountId> participants) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !entities.isJoinedToTransaction()) {
            throw new IllegalStateException("Account locks require the owning financial transaction");
        }
        var result = new LinkedHashMap<AccountId, AccountEntity>();
        // ADR-010: sequential point reads follow application order, including missing-key range protection.
        for (var id : participants.stream().distinct().sorted(AccountLockOrder.DESCENDING).toList()) {
            var rows = entities.createNativeQuery("SELECT account_id, category, currency, balance, status, created_at "
                    + "FROM dbo.accounts WITH (UPDLOCK,HOLDLOCK) WHERE account_id=CAST(? AS uniqueidentifier)", AccountEntity.class)
                    .setParameter(1, id.value().toString()).setHint("jakarta.persistence.query.timeout", 2000).getResultList();
            if (!rows.isEmpty()) {
                var account = (AccountEntity) rows.getFirst();
                // A native entity query can reuse managed state. Refresh only after taking the database lock.
                entities.refresh(account);
                result.put(id, account);
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
