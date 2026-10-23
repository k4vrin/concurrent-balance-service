package dev.kavrin.balance.operation.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.Immutable;

/** Retained storage mapping; no ORM cascades or history mutation API. */
@Entity
@Immutable
@Table(name = "operations", schema = "dbo")
public class OperationEntity {
    @Id @Column(name = "operation_id") private UUID id;
    @JdbcTypeCode(SqlTypes.NVARCHAR) @Column(name = "transaction_id", length = 256) private String transactionId;
    @Column(name = "operation_type", length = 16) private String operationType;
    @Column(name = "source_account_id") private UUID sourceAccountId;
    @Column(name = "destination_account_id") private UUID destinationAccountId;
    @Column(name = "amount") private long amount;
    @JdbcTypeCode(SqlTypes.NCHAR) @Column(name = "currency", length = 3) private String currency;
    @Column(name = "canonical_format_version") private int canonicalFormatVersion;
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY) @Column(name = "canonical_request", columnDefinition = "varbinary(max)") private byte[] canonicalRequest;
    @JdbcTypeCode(SqlTypes.BINARY) @Column(name = "canonical_fingerprint", length = 32) private byte[] canonicalFingerprint;
    @JdbcTypeCode(SqlTypes.NVARCHAR) @Column(name = "execution_policy_version", length = 128) private String executionPolicyVersion;
    @Column(name = "funding_account_id") private UUID fundingAccountId;
    @JdbcTypeCode(SqlTypes.NVARCHAR) @Column(name = "funding_policy_version", length = 128) private String fundingPolicyVersion;
    @JdbcTypeCode(SqlTypes.BINARY) @Column(name = "funded_fingerprint", length = 32) private byte[] fundedFingerprint;
    @Column(name = "state", length = 16) private String state;
    @Column(name = "rejection_reason", length = 64) private String rejectionReason;
    @Column(name = "response_format_version") private Integer responseFormatVersion;
    @Column(name = "http_status") private Short httpStatus;
    @Column(name = "response_content_type", length = 64) private String responseContentType;
    @JdbcTypeCode(SqlTypes.LONG32VARBINARY) @Column(name = "response_body", columnDefinition = "varbinary(max)") private byte[] responseBody;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;

    protected OperationEntity() { }

    public String state() { return state; }
    public byte[] canonicalRequest() { return canonicalRequest.clone(); }
    public byte[] responseBody() { return responseBody == null ? null : responseBody.clone(); }
}
