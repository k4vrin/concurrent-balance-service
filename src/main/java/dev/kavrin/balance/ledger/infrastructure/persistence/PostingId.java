package dev.kavrin.balance.ledger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

/**
 * Maps the composite identity of a persisted line within one journal.
 *
 * <p>Line numbers identify immutable posting positions rather than independently mutable records.
 * The reviewed schema enforces valid line numbers and journal references.
 *
 * @param journalId journal owning the posting
 * @param lineNumber posting position within that journal
 */
@Embeddable
public record PostingId(@Column(name = "journal_id") UUID journalId,
                        @Column(name = "line_number") int lineNumber) implements Serializable { }
