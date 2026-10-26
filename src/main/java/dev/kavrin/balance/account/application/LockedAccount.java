package dev.kavrin.balance.account.application;

import dev.kavrin.balance.account.domain.Account;

/** Account snapshot read under the owning transaction's participant lock; never a JPA entity. */
public record LockedAccount(Account snapshot, boolean active) { }
