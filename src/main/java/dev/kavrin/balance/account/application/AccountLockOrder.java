package dev.kavrin.balance.account.application;

import java.util.Comparator;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountId;

/**
 * Defines the shared descending total order for acquiring account locks.
 *
 * <p>Comparison treats both UUID halves as unsigned and is independent of transfer direction,
 * Java UUID natural ordering and SQL Server GUID ordering. This utility orders identifiers; the
 * persistence adapter must acquire locks sequentially in that order (ADR-009 and ADR-010).
 */
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
