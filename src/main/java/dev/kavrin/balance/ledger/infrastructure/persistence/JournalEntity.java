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
@Table(name = "journals", schema = "dbo")
public class JournalEntity {
    @Id @Column(name = "journal_id") private UUID id;
    @Column(name = "operation_id") private UUID operationId;
    @JdbcTypeCode(SqlTypes.NCHAR) @Column(name = "currency", length = 3) private String currency;
    @Column(name = "created_at") private LocalDateTime createdAt;

    protected JournalEntity() { }
}
