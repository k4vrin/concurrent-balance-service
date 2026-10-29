package dev.kavrin.balance.ledger.domain;

/**
 * Identifies the debit or credit side of a ledger line.
 *
 * <p>A side is not an arithmetic sign by itself: customer liabilities and funding accounts have
 * different normal sides, resolved when calculating balance projections (ADR-007).
 */
public enum PostingSide {
    DEBIT,
    CREDIT
}
