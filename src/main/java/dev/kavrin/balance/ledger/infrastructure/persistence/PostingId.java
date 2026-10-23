package dev.kavrin.balance.ledger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record PostingId(@Column(name = "journal_id") UUID journalId,
                        @Column(name = "line_number") int lineNumber) implements Serializable { }
