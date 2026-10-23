package dev.kavrin.balance.account.application;

import java.util.Comparator;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountId;

public final class AccountLockOrder {

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
        int comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0 ? comparison : Long.compareUnsigned(
                left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };

    // All account writers must share this order, independently of transfer direction
    // and SQL Server GUID ordering. It orders IDs; it does not acquire database locks.
    public static final Comparator<AccountId> DESCENDING =
            Comparator.comparing(AccountId::value, UNSIGNED_UUID_ORDER).reversed();

    private AccountLockOrder() {
    }
}
