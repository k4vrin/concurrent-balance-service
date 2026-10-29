package dev.kavrin.balance.account.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Wraps a required UUID as an account identity without assigning database lock order.
 *
 * <p>Parsing and HTTP shape validation belong to the adapter; all account writers use the separate
 * application lock comparator when resolving multiple identifiers.
 *
 * @param value non-null account UUID
 */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "Account ID is required");
    }
}
