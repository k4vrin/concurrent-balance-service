package dev.kavrin.balance.account.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.money.Money;
import java.util.Currency;

@Entity
@Table(name = "accounts", schema = "dbo")
public class AccountEntity {
    @Id @Column(name = "account_id", updatable = false) private UUID id;
    @Enumerated(EnumType.STRING) @Column(updatable = false, length = 32) private AccountCategory category;
    @JdbcTypeCode(SqlTypes.NCHAR) @Column(updatable = false, length = 3) private String currency;
    @Column(nullable = false) private long balance;
    @Column(updatable = false, length = 16) private String status;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;

    protected AccountEntity() { }

    public Account snapshot() {
        return new Account(new AccountId(id), category, new Money(balance, Currency.getInstance(currency)));
    }

    public boolean active() { return "ACTIVE".equals(status); }

    public void project(Account proposed) {
        var current = snapshot();
        if (!current.id().equals(proposed.id()) || current.category() != proposed.category()
                || !current.balance().currency().equals(proposed.balance().currency())) {
            throw new IllegalArgumentException("Projection cannot change account identity, category or currency");
        }
        balance = proposed.balance().amount();
    }
}
