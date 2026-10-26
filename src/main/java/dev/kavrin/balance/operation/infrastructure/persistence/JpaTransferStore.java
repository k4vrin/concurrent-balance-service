package dev.kavrin.balance.operation.infrastructure.persistence;

import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.account.infrastructure.persistence.AccountEntity;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import dev.kavrin.balance.operation.application.identity.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import dev.kavrin.balance.operation.application.transfer.TransferAttempt;
import dev.kavrin.balance.operation.application.transfer.TransferStore;
import dev.kavrin.balance.operation.domain.TransactionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SQL Server implementation of the transfer persistence port. Native writes retain explicit
 * reservation/completion predicates and ordering; managed accounts supply balance projections.
 * Named SQL parameters and a typed replay row keep positional database details out of the flow.
 */
@Repository
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
public class JpaTransferStore implements TransferStore {
    private static final int QUERY_TIMEOUT_MILLIS = 2000;
    private static final String TRANSACTION_ID_CONSTRAINT = "uq_operations_transaction_id";
    private static final String FIND_OPERATION = """
            SELECT canonical_format_version, canonical_request, operation_type, currency, state,
                   rejection_reason, response_format_version, http_status, response_content_type, response_body
            FROM dbo.operations
            WHERE transaction_id = :transactionId
            """;
    private static final String RESERVE_OPERATION = """
            INSERT dbo.operations (
                operation_id, transaction_id, operation_type, source_account_id, destination_account_id, amount, currency,
                canonical_format_version, canonical_request, canonical_fingerprint, execution_policy_version, state
            ) VALUES (
                CAST(:operationId AS uniqueidentifier), :transactionId, 'TRANSFER',
                CAST(:sourceAccountId AS uniqueidentifier), CAST(:destinationAccountId AS uniqueidentifier), :amount, :currency,
                :canonicalVersion, :canonicalRequest, :canonicalFingerprint, :executionPolicyVersion, 'PENDING'
            )
            """;
    private static final String INSERT_JOURNAL = """
            INSERT dbo.journals (journal_id, operation_id, currency)
            VALUES (CAST(:journalId AS uniqueidentifier), CAST(:operationId AS uniqueidentifier), :currency)
            """;
    private static final String INSERT_POSTING = """
            INSERT dbo.postings (journal_id, line_number, account_id, currency, side, amount)
            VALUES (CAST(:journalId AS uniqueidentifier), :lineNumber,
                    CAST(:accountId AS uniqueidentifier), :currency, :side, :amount)
            """;
    private static final String COMPLETE_OPERATION = """
            UPDATE dbo.operations
            SET state = :state, rejection_reason = CAST(:rejectionReason AS varchar(64)),
                completed_at = SYSUTCDATETIME(), response_format_version = :responseVersion,
                http_status = :httpStatus, response_content_type = :contentType, response_body = :body
            WHERE operation_id = CAST(:operationId AS uniqueidentifier) AND state = 'PENDING'
            """;

    private final EntityManager entities;
    private final AccountStore accounts;

    public JpaTransferStore(EntityManager entities, AccountStore accounts) {
        this.entities = entities;
        this.accounts = accounts;
    }

    @Override
    public Optional<MutationResult> replay(TransferCommand command, CanonicalFingerprint canonical) {
        requireTransaction();
        return findStoredOperation(command.transactionId()).map(stored -> {
            // ADR-010: compare full caller intent before decoding the retained outcome/response.
            stored.requireMatching(canonical);
            return stored.toResult(command.transactionId());
        });
    }

    /** Maps native column labels at one boundary; SQL and driver types still require SQL Server verification. */
    private Optional<StoredOperation> findStoredOperation(TransactionId transactionId) {
        List<?> rows = entities.createNativeQuery(FIND_OPERATION, Tuple.class)
                .setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLIS)
                .setParameter("transactionId", transactionId.value()).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(StoredOperation.from(Tuple.class.cast(rows.getFirst())));
    }

    @Override
    public void reserve(TransferAttempt attempt) {
        requireTransaction();
        var command = attempt.command();
        var canonical = attempt.canonical().fingerprint();
        try {
            query(RESERVE_OPERATION)
                    .setParameter("operationId", attempt.operationId().toString())
                    .setParameter("transactionId", command.transactionId().value())
                    .setParameter("sourceAccountId", command.sourceAccountId().value().toString())
                    .setParameter("destinationAccountId", command.destinationAccountId().value().toString())
                    .setParameter("amount", command.amount().amount())
                    .setParameter("currency", command.amount().currency().getCurrencyCode())
                    .setParameter("canonicalVersion", canonical.version())
                    .setParameter("canonicalRequest", canonical.bytes())
                    .setParameter("canonicalFingerprint", HexFormat.of().parseHex(canonical.sha256()))
                    .setParameter("executionPolicyVersion", attempt.policy().version())
                    .executeUpdate();
        } catch (RuntimeException failure) {
            if (isTransactionIdConflict(failure)) {
                throw new DuplicateOperation(failure);
            }
            throw failure;
        }
    }

    @Override
    public Map<AccountId, LockedAccount> lockAccounts(List<AccountId> participants) {
        return accounts.lockAll(participants).entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> new LockedAccount(entry.getValue().snapshot(), entry.getValue().active())));
    }

    @Override
    public void appendJournal(TransferAttempt attempt, Journal journal) {
        requireTransaction();
        query(INSERT_JOURNAL)
                .setParameter("journalId", attempt.journalId().toString())
                .setParameter("operationId", attempt.operationId().toString())
                .setParameter("currency", attempt.command().amount().currency().getCurrencyCode()).executeUpdate();
        int line = 1;
        for (var posting : journal.postings()) {
            query(INSERT_POSTING)
                    .setParameter("journalId", attempt.journalId().toString()).setParameter("lineNumber", line++)
                    .setParameter("accountId", posting.accountId().value().toString())
                    .setParameter("currency", posting.amount().currency().getCurrencyCode())
                    .setParameter("side", posting.side().name()).setParameter("amount", posting.amount().amount()).executeUpdate();
        }
    }

    @Override
    public void applyProjections(Map<AccountId, Account> projections) {
        requireTransaction();
        projections.forEach((id, proposed) -> entities.find(AccountEntity.class, id.value()).project(proposed));
        entities.flush();
    }

    @Override
    public void complete(TransferAttempt attempt, MutationResult result, StoredResponse response) {
        requireTransaction();
        var completion = OperationCompletion.from(result.outcome());
        int updated = query(COMPLETE_OPERATION)
                .setParameter("state", completion.state()).setParameter("rejectionReason", completion.rejectionReason())
                .setParameter("responseVersion", response.version()).setParameter("httpStatus", response.status())
                .setParameter("contentType", response.contentType()).setParameter("body", response.body())
                .setParameter("operationId", attempt.operationId().toString()).executeUpdate();
        if (updated != 1) { throw new IllegalStateException("Only a reserved operation can complete once"); }
    }

    private Query query(String sql) {
        return entities.createNativeQuery(sql).setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_MILLIS);
    }

    /** Only the named request-identity constraint permits duplicate recovery; other integrity failures propagate. */
    private static boolean isTransactionIdConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 2627 || sql.getErrorCode() == 2601)
                    && sql.getMessage() != null && sql.getMessage().contains(TRANSACTION_ID_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !entities.isJoinedToTransaction()) {
            throw new IllegalStateException("Transfer persistence requires the owning writable transaction");
        }
    }
}
