package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.ledger.domain.*;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.*;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Test-only internal opening path. It never registers a public endpoint or a production write handler. */
final class OpeningFixture {
    enum FailurePoint { NONE, AFTER_JOURNAL, AFTER_POSTINGS, AFTER_PROJECTIONS }
    record Opened(UUID operationId, UUID journalId, String transactionId, byte[] canonical) { }
    private final EntityManager entities;
    private final AccountStore accounts;
    private final TransactionTemplate transaction;

    OpeningFixture(EntityManager entities, AccountStore accounts, PlatformTransactionManager manager) {
        this.entities = entities;
        this.accounts = accounts;
        this.transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    Opened open(AccountId customer, AccountId funding, Money amount, FailurePoint failure) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Opening fixture must own its transaction");
        }
        if (amount.amount() <= 0 || amount.amount() > 10_000_000_000_000L
                || !amount.currency().getCurrencyCode().equals("IRR")) {
            throw new IllegalArgumentException("Fixture opening must follow the initial IRR limits");
        }
        var canonical = CanonicalRequest.opening(customer, amount, Optional.empty());
        var selection = new FundingSelection(funding, "fixture-v1");
        var result = new Opened(UUID.randomUUID(), UUID.randomUUID(), "opening-" + UUID.randomUUID(), canonical.fingerprint().bytes());
        return transaction.execute(status -> {
            // ADR-010: reserve before account locks; failure must roll back this identity too.
            reserve(result, customer, amount, canonical, selection);
            var locked = accounts.lockAll(List.of(customer, funding));
            if (locked.size() != 2 || !locked.get(customer).active() || !locked.get(funding).active()
                    || locked.get(customer).snapshot().category() != AccountCategory.CUSTOMER_LIABILITY
                    || locked.get(funding).snapshot().category() != AccountCategory.FUNDING_CLEARING
                    || locked.get(customer).snapshot().balance().amount() != 0) {
                throw new IllegalStateException("Opening requires an active zero customer and synthetic funding account");
            }
            var journal = new Journal(List.of(new Posting(funding, PostingSide.DEBIT, amount),
                    new Posting(customer, PostingSide.CREDIT, amount)));
            var snapshots = locked.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().snapshot()));
            var projections = journal.projectBalances(snapshots);
            entities.createNativeQuery("INSERT dbo.journals (journal_id,operation_id,currency) VALUES (CAST(? AS uniqueidentifier),CAST(? AS uniqueidentifier),?)")
                    .setParameter(1, result.journalId().toString()).setParameter(2, result.operationId().toString())
                    .setParameter(3, amount.currency().getCurrencyCode()).executeUpdate();
            failAt(failure, FailurePoint.AFTER_JOURNAL);
            int line = 1;
            for (var posting : journal.postings()) {
                entities.createNativeQuery("INSERT dbo.postings (journal_id,line_number,account_id,currency,side,amount) VALUES (CAST(? AS uniqueidentifier),?,CAST(? AS uniqueidentifier),?,?,?)")
                        .setParameter(1, result.journalId().toString()).setParameter(2, line++)
                        .setParameter(3, posting.accountId().value().toString()).setParameter(4, amount.currency().getCurrencyCode())
                        .setParameter(5, posting.side().name()).setParameter(6, posting.amount().amount()).executeUpdate();
            }
            failAt(failure, FailurePoint.AFTER_POSTINGS);
            // ADR-007: all proposed snapshots were validated before any projection mutation.
            projections.forEach((id, proposed) -> locked.get(id).project(proposed));
            entities.flush();
            failAt(failure, FailurePoint.AFTER_PROJECTIONS);
            byte[] response = ("{\"transactionId\":\"" + result.transactionId()
                    + "\",\"operationType\":\"OPENING\",\"currency\":\"IRR\",\"status\":\"SUCCEEDED\"}").getBytes(StandardCharsets.UTF_8);
            entities.createNativeQuery("UPDATE dbo.operations SET state='SUCCEEDED',completed_at=SYSUTCDATETIME(),response_format_version=1,"
                    + "http_status=200,response_content_type='application/json',response_body=? WHERE operation_id=CAST(? AS uniqueidentifier)")
                    .setParameter(1, response).setParameter(2, result.operationId().toString()).executeUpdate();
            // Only the template's successful return proves commit; no callback value escapes earlier.
            return result;
        });
    }

    private void reserve(Opened result, AccountId customer, Money amount, CanonicalRequest canonical, FundingSelection selection) {
        entities.createNativeQuery("INSERT dbo.operations (operation_id,transaction_id,operation_type,destination_account_id,amount,currency,"
                + "canonical_format_version,canonical_request,canonical_fingerprint,execution_policy_version,funding_account_id,funding_policy_version,funded_fingerprint,state) "
                + "VALUES (CAST(? AS uniqueidentifier),?,'OPENING',CAST(? AS uniqueidentifier),?,?,?,?,?,N'fixture-v1',CAST(? AS uniqueidentifier),?,?,'PENDING')")
                .setParameter(1, result.operationId().toString()).setParameter(2, result.transactionId())
                .setParameter(3, customer.value().toString()).setParameter(4, amount.amount()).setParameter(5, amount.currency().getCurrencyCode())
                .setParameter(6, canonical.fingerprint().version()).setParameter(7, canonical.fingerprint().bytes())
                .setParameter(8, HexFormat.of().parseHex(canonical.fingerprint().sha256()))
                .setParameter(9, selection.counterpartAccountId().value().toString()).setParameter(10, selection.policyVersion())
                .setParameter(11, HexFormat.of().parseHex(canonical.fundedFingerprint(selection).sha256())).executeUpdate();
    }

    private static void failAt(FailurePoint requested, FailurePoint current) {
        if (requested == current) { throw new IllegalStateException("Injected opening failure: " + current); }
    }
}
