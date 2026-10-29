package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Retains immutable HTTP mutation intent so identical or conflicting payloads can target either application.
 *
 * <p>The customer is a credit target or a debit/transfer source; the recipient is used only for
 * transfer. All identifiers and amounts come from controlled fixtures. Each request uses the
 * unchanged public JSON contract and a bounded client timeout.
 *
 * @param type public mutation type, excluding internal opening operations
 * @param transactionId shared retry identity for the race
 * @param customer credit target or debit/transfer source
 * @param recipient destination used only by transfers
 * @param amount whole-rial amount from the fixture
 */
record MutationRaceRequest(OperationType type, TransactionId transactionId, AccountId customer, AccountId recipient, long amount) {
    HttpRequest at(RunningLedgerApplication application) {
        String path;
        String body = "{\"transactionId\":\"" + transactionId.value() + "\",\"amountRial\":" + amount + ",\"currency\":\"IRR\"";
        switch (type) {
            case CREDIT -> path = "/api/v1/accounts/" + customer.value() + "/credits";
            case DEBIT -> path = "/api/v1/accounts/" + customer.value() + "/debits";
            case TRANSFER -> {
                path = "/api/v1/transfers";
                body += ",\"sourceAccountId\":\"" + customer.value() + "\",\"destinationAccountId\":\"" + recipient.value() + "\"";
            }
            default -> throw new IllegalArgumentException("Only public mutations participate in an HTTP race");
        }
        return HttpRequest.newBuilder(application.uri(path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body + "}")).build();
    }
}
