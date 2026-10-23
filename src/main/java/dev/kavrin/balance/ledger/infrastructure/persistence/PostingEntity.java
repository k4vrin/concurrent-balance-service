package dev.kavrin.balance.ledger.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Immutable;

/** Retained storage mapping; no ORM cascades or history mutation API. */
@Entity
@Immutable
@Table(name = "postings", schema = "dbo")
public class PostingEntity {
    @EmbeddedId private PostingId id;
    @Column(name = "account_id") private UUID accountId;
    @JdbcTypeCode(SqlTypes.NCHAR) @Column(name = "currency", length = 3) private String currency;
    @Column(name = "side", length = 6) private String side;
    @Column(name = "amount") private long amount;

    protected PostingEntity() { }
}
