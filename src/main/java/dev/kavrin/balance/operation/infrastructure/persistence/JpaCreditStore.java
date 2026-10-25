package dev.kavrin.balance.operation.infrastructure.persistence;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.account.infrastructure.persistence.AccountEntity;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.operation.application.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.CreditAttempt;
import dev.kavrin.balance.operation.application.CreditCommand;
import dev.kavrin.balance.operation.application.CreditStore;
import dev.kavrin.balance.operation.application.DuplicateOperation;
import dev.kavrin.balance.operation.application.MutationResult;
import dev.kavrin.balance.operation.application.OperationFailure;
import dev.kavrin.balance.operation.application.StoredResponse;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
public class JpaCreditStore implements CreditStore {
    private final EntityManager entities;
    private final AccountStore accounts;

    public JpaCreditStore(EntityManager entities, AccountStore accounts) {
        this.entities = entities;
        this.accounts = accounts;
    }

    @Override
    public Optional<MutationResult> replay(CreditCommand command, CanonicalFingerprint canonical) {
        requireTransaction();
        var rows = query("SELECT canonical_format_version,canonical_request,operation_type,currency,state,rejection_reason,"
                + "response_format_version,http_status,response_content_type,response_body FROM dbo.operations WHERE transaction_id=?")
                .setParameter(1, command.transactionId().value()).getResultList();
        if (rows.isEmpty()) { return Optional.empty(); }
        var row = (Object[]) rows.getFirst();
        if (((Number) row[0]).intValue() != canonical.version()) {
            throw new IllegalStateException("Unsupported stored canonical version");
        }
        if (!Arrays.equals((byte[]) row[1], canonical.bytes())) {
            throw new OperationFailure(OperationFailure.Reason.TRANSACTION_ID_CONFLICT);
        }
        var type = OperationType.valueOf((String) row[2]);
        OperationOutcome outcome = switch ((String) row[4]) {
            case "SUCCEEDED" -> new OperationOutcome.Success(command.transactionId(), type);
            case "REJECTED" -> new OperationOutcome.Rejected(command.transactionId(), type, RejectionReason.valueOf((String) row[5]));
            default -> throw new IllegalStateException("Committed operation has no terminal outcome");
        };
        var response = new StoredResponse(((Number) row[6]).intValue(), ((Number) row[7]).intValue(),
                (String) row[8], (byte[]) row[9]);
        return Optional.of(new MutationResult(outcome, Currency.getInstance((String) row[3]), Optional.of(response)));
    }

    @Override
    public void reserve(CreditAttempt attempt) {
        requireTransaction();
        var command = attempt.command();
        var canonical = attempt.canonical().fingerprint();
        try {
            query("INSERT dbo.operations (operation_id,transaction_id,operation_type,destination_account_id,amount,currency,"
                    + "canonical_format_version,canonical_request,canonical_fingerprint,execution_policy_version,funding_account_id,"
                    + "funding_policy_version,funded_fingerprint,state) VALUES (CAST(? AS uniqueidentifier),?,'CREDIT',"
                    + "CAST(? AS uniqueidentifier),?,?,?,?,?,?,CAST(? AS uniqueidentifier),?,?,'PENDING')")
                    .setParameter(1, attempt.operationId().toString()).setParameter(2, command.transactionId().value())
                    .setParameter(3, command.accountId().value().toString()).setParameter(4, command.amount().amount())
                    .setParameter(5, command.amount().currency().getCurrencyCode()).setParameter(6, canonical.version())
                    .setParameter(7, canonical.bytes()).setParameter(8, HexFormat.of().parseHex(canonical.sha256()))
                    .setParameter(9, attempt.policy().version()).setParameter(10, attempt.funding().counterpartAccountId().value().toString())
                    .setParameter(11, attempt.funding().policyVersion())
                    .setParameter(12, HexFormat.of().parseHex(attempt.canonical().fundedFingerprint(attempt.funding()).sha256()))
                    .executeUpdate();
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && (sql.getErrorCode() == 2627 || sql.getErrorCode() == 2601)
                        && sql.getMessage().contains("uq_operations_transaction_id")) {
                    throw new DuplicateOperation(failure);
                }
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
    public void appendJournal(CreditAttempt attempt, Journal journal) {
        requireTransaction();
        query("INSERT dbo.journals (journal_id,operation_id,currency) VALUES (CAST(? AS uniqueidentifier),CAST(? AS uniqueidentifier),?)")
                .setParameter(1, attempt.journalId().toString()).setParameter(2, attempt.operationId().toString())
                .setParameter(3, attempt.command().amount().currency().getCurrencyCode()).executeUpdate();
        int line = 1;
        for (var posting : journal.postings()) {
            query("INSERT dbo.postings (journal_id,line_number,account_id,currency,side,amount) VALUES (CAST(? AS uniqueidentifier),?,CAST(? AS uniqueidentifier),?,?,?)")
                    .setParameter(1, attempt.journalId().toString()).setParameter(2, line++)
                    .setParameter(3, posting.accountId().value().toString()).setParameter(4, posting.amount().currency().getCurrencyCode())
                    .setParameter(5, posting.side().name()).setParameter(6, posting.amount().amount()).executeUpdate();
        }
    }

    @Override
    public void applyProjections(Map<AccountId, Account> projections) {
        requireTransaction();
        projections.forEach((id, proposed) -> entities.find(AccountEntity.class, id.value()).project(proposed));
        entities.flush();
    }

    @Override
    public void complete(CreditAttempt attempt, MutationResult result, StoredResponse response) {
        requireTransaction();
        boolean rejected = result.outcome() instanceof OperationOutcome.Rejected;
        var reason = rejected ? ((OperationOutcome.Rejected) result.outcome()).reason().name() : null;
        int updated = query("UPDATE dbo.operations SET state=?,rejection_reason=CAST(? AS varchar(64)),completed_at=SYSUTCDATETIME(),response_format_version=?,"
                + "http_status=?,response_content_type=?,response_body=? WHERE operation_id=CAST(? AS uniqueidentifier) AND state='PENDING'")
                .setParameter(1, rejected ? "REJECTED" : "SUCCEEDED").setParameter(2, reason).setParameter(3, response.version())
                .setParameter(4, response.status()).setParameter(5, response.contentType()).setParameter(6, response.body())
                .setParameter(7, attempt.operationId().toString()).executeUpdate();
        if (updated != 1) { throw new IllegalStateException("Only a reserved operation can complete once"); }
    }

    private Query query(String sql) {
        return entities.createNativeQuery(sql).setHint("jakarta.persistence.query.timeout", 2000);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !entities.isJoinedToTransaction()) {
            throw new IllegalStateException("Credit persistence requires the owning writable transaction");
        }
    }
}
