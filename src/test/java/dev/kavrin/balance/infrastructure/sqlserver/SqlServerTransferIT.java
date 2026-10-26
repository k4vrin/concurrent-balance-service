package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.ConcurrentBalanceServiceApplication;
import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.application.policy.AmountLimits;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.SupportedCurrencies;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.application.transfer.TransferHandler;
import dev.kavrin.balance.operation.application.transfer.TransferResponseEncoder;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaTransferStore;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqlServerTransferIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final long CAP = 10_000_000_000_000L;
    private static final AccountId FUNDING = new AccountId(UUID.randomUUID());
    @Autowired TransferHandler handler;
    @Autowired EntityManager entities;
    @Autowired CreditHandler credits;
    @Autowired OperationCoordinator coordinator;
    @Autowired TransferResponseEncoder encoder;
    @Autowired ExecutionPolicy policy;
    @Autowired Environment environment;
    @MockitoSpyBean JpaTransferStore store;

    @DynamicPropertySource
    static void databaseAndPolicy(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
        properties.add("spring.datasource.url", () -> System.getenv("SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SQLSERVER_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("SQLSERVER_TEST_PASSWORD"));
        properties.add("balance.execution.enabled", () -> "true");
        properties.add("balance.execution.funding[IRR].account-id", () -> FUNDING.value().toString());
        properties.add("balance.execution.funding[IRR].policy-version", () -> "credit-it-funding-v1");
    }

    @BeforeAll
    static void committedZeroFunding() throws Exception { createAccount(FUNDING, "FUNDING_CLEARING"); }

    @Test
    void exactFundsHttpTransferConservesTotalAndReplaysWithoutMovingAgain() throws Exception {
        var source = fundedCustomer(100);
        var destination = fundedCustomer(20);
        long fundingBefore = balance(FUNDING);
        var command = command(source, destination, 100);
        var first = post(command);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(first.body()).isEqualTo(retainedBody(command));
        assertThat(balance(source)).isZero();
        assertThat(balance(destination)).isEqualTo(120);
        assertThat(balance(source) + balance(destination)).isEqualTo(120);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        assertThat(post(command).body()).isEqualTo(first.body());
        assertThat(balance(destination)).isEqualTo(120);
        assertCounts(command, 1, 1, 2);
        assertBalanced(command);
        assertReconciles(source);
        assertReconciles(destination);
        assertTransferIntent(command);
    }

    @Test
    void insufficientFundsRemainsRejectedAfterLaterCredit() throws Exception {
        var source = fundedCustomer(100);
        var destination = customer();
        var command = command(source, destination, 101);
        var rejected = post(command);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(new String(rejected.body(), StandardCharsets.UTF_8)).contains("INSUFFICIENT_FUNDS", "/api/v1/transfers");
        assertThat(rejected.body()).isEqualTo(retainedBody(command));
        credit(source, 200);
        assertThat(post(command).body()).isEqualTo(rejected.body());
        assertThat(balance(source)).isEqualTo(300);
        assertThat(balance(destination)).isZero();
        assertCounts(command, 1, 0, 0);
        assertThat(post(command(source, destination, 101)).statusCode()).isEqualTo(200);
        assertThat(balance(source)).isEqualTo(199);
        assertThat(balance(destination)).isEqualTo(101);
        assertReconciles(source);
        assertReconciles(destination);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "source", "destination", "operation"})
    void conflictingReuseNeverAddsAnotherFinancialEffect(String changed) throws Exception {
        var customer = fundedCustomer(100);
        var original = command(customer, 40);
        assertThat(post(original).statusCode()).isEqualTo(200);
        switch (changed) {
            case "amount" -> assertThat(post(new TransferCommand(original.transactionId(), customer, original.destinationAccountId(), new Money(41, IRR))).statusCode()).isEqualTo(409);
            case "source" -> assertThat(post(new TransferCommand(original.transactionId(), customer(), original.destinationAccountId(), original.amount())).statusCode()).isEqualTo(409);
            case "destination" -> assertThat(post(new TransferCommand(original.transactionId(), customer, customer(), original.amount())).statusCode()).isEqualTo(409);
            case "operation" -> assertThatThrownBy(() -> credits.handle(new CreditCommand(original.transactionId(), customer, original.amount())))
                    .isInstanceOfSatisfying(OperationFailure.class, failure -> assertThat(failure.reason()).isEqualTo(OperationFailureReason.TRANSACTION_ID_CONFLICT));
            default -> throw new AssertionError(changed);
        }
        assertCounts(original, 1, 1, 2);
        assertThat(balance(customer)).isEqualTo(60);
        assertReconciles(customer);
    }

    @Test
    void successAndRejectionSurviveApplicationAndPoolRecreation() throws Exception {
        var customer = fundedCustomer(100);
        var success = command(customer, 40);
        var rejected = command(customer, 61);
        byte[] originalSuccess;
        byte[] originalRejection;
        try (var first = independentApplication()) {
            var fresh = first.getBean(TransferHandler.class);
            originalSuccess = fresh.handle(success).storedResponse().orElseThrow().body();
            originalRejection = fresh.handle(rejected).storedResponse().orElseThrow().body();
        }
        credit(customer, 200);
        try (var second = independentApplication()) {
            var fresh = second.getBean(TransferHandler.class);
            assertThat(fresh.handle(success).storedResponse().orElseThrow().body()).isEqualTo(originalSuccess);
            assertThat(fresh.handle(rejected).storedResponse().orElseThrow().body()).isEqualTo(originalRejection);
        }
        assertThat(balance(customer)).isEqualTo(260);
        assertCounts(success, 1, 1, 2);
        assertCounts(rejected, 1, 0, 0);
    }

    @Test
    void rejectionReplaysBeforeDisabledCurrencyAndChangedCaps() throws Exception {
        var command = command(fundedCustomer(100), 101);
        var original = handler.handle(command);
        var disabled = new ExecutionPolicy("disabled", new SupportedCurrencies(Set.of()), new AmountLimits(1, 1), Map.of());
        var fresh = new TransferHandler(coordinator, disabled, store, encoder);
        assertThat(fresh.handle(command).storedResponse().orElseThrow().body()).isEqualTo(original.storedResponse().orElseThrow().body());
        assertCounts(command, 1, 0, 0);
        assertThat(balance(command.sourceAccountId())).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingAccountRejectionStaysTerminalAfterAccountCreation(boolean sourceMissing) throws Exception {
        var id = new AccountId(UUID.randomUUID());
        var existing = fundedCustomer(100);
        var command = sourceMissing ? command(id, existing, 1) : command(existing, id, 1);
        var original = post(command);
        assertThat(original.statusCode()).isEqualTo(404);
        createAccount(id, "CUSTOMER_LIABILITY");
        assertThat(post(command).body()).isEqualTo(original.body());
        assertThat(balance(id)).isZero();
        assertCounts(command, 1, 0, 0);
    }

    @Test
    void transactionCapRejectionIsRetainedWithoutFinancialWrites() throws Exception {
        var command = command(customer(), CAP + 1);
        var original = post(command);
        assertThat(original.statusCode()).isEqualTo(409);
        assertThat(new String(original.body(), StandardCharsets.UTF_8)).contains("TRANSACTION_LIMIT_EXCEEDED");
        var higher = new ExecutionPolicy("higher", policy.supportedCurrencies(), new AmountLimits(CAP * 2, CAP * 2), policy.fundingByCurrency());
        var fresh = new TransferHandler(coordinator, higher, store, encoder);
        assertThat(fresh.handle(command).storedResponse().orElseThrow().body()).isEqualTo(original.body());
        assertCounts(command, 1, 0, 0);
        assertThat(balance(command.sourceAccountId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal", "source", "projections", "outcome"})
    void failureAfterEachWriteRollsBackHistoryBothBalancesAndIdentity(String boundary) throws Exception {
        var customer = fundedCustomer(100);
        var command = command(customer, 40);
        long fundingBefore = balance(FUNDING);
        long destinationBefore = balance(command.destinationAccountId());
        switch (boundary) {
            case "journal" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected transfer failure"); })
                    .when(store).appendJournal(any(), any());
            case "source" -> doAnswer(call -> {
                entities.createNativeQuery("UPDATE dbo.accounts SET balance=:balance WHERE account_id=CAST(:id AS uniqueidentifier)")
                        .setParameter("balance", 60L).setParameter("id", customer.value().toString()).executeUpdate();
                throw new IllegalStateException("injected failure after source write");
            }).when(store).applyProjections(any());
            case "projections" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected transfer failure"); })
                    .when(store).applyProjections(any());
            case "outcome" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected transfer failure"); })
                    .when(store).complete(any(), any(), any());
            default -> throw new AssertionError(boundary);
        }
        assertThat(post(command).statusCode()).isEqualTo(503);
        assertCounts(command, 0, 0, 0);
        assertThat(balance(customer)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        assertThat(balance(command.destinationAccountId())).isEqualTo(destinationBefore);
        Mockito.reset(store);
        assertThat(post(command).statusCode()).isEqualTo(200);
        assertCounts(command, 1, 1, 2);
        assertThat(balance(customer)).isEqualTo(60);
        assertThat(balance(command.destinationAccountId())).isEqualTo(destinationBefore + 40);
        assertReconciles(command.destinationAccountId());
        assertReconciles(customer);
        assertReconciles(FUNDING, true);
    }

    @Test
    void aRealBlockedAccountTimesOutWithoutRetainingABusinessRejection() throws Exception {
        var customer = fundedCustomer(100);
        var command = command(customer, 40);
        long fundingBefore = balance(FUNDING);
        try (var blocker = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("UPDATE dbo.accounts SET balance=balance WHERE account_id=CAST(? AS uniqueidentifier)")) {
                statement.setString(1, customer.value().toString());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            assertThat(post(command).statusCode()).isEqualTo(503);
            assertCounts(command, 0, 0, 0);
            blocker.rollback();
        }
        assertThat(balance(customer)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        assertThat(post(command).statusCode()).isEqualTo(200);
        assertCounts(command, 1, 1, 2);
    }

    @Test
    void recipientCanReachTheExactBalanceCap() throws Exception {
        var source = fundedCustomer(1);
        var destination = fundedCustomer(CAP - 1);
        var command = command(source, destination, 1);
        assertThat(post(command).statusCode()).isEqualTo(200);
        assertThat(balance(source)).isZero();
        assertThat(balance(destination)).isEqualTo(CAP);
        assertCounts(command, 1, 1, 2);
        assertBalanced(command);
        assertReconciles(source);
        assertReconciles(destination);
    }

    @Test
    void recipientCapRejectsWithoutPartialWritesAndReplaysAfterPolicyChange() throws Exception {
        var source = fundedCustomer(100);
        var destination = fundedCustomer(CAP);
        var command = command(source, destination, 1);
        var rejected = post(command);
        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(new String(rejected.body(), StandardCharsets.UTF_8)).contains("BALANCE_LIMIT_EXCEEDED");
        assertThat(balance(source)).isEqualTo(100);
        assertThat(balance(destination)).isEqualTo(CAP);
        assertCounts(command, 1, 0, 0);
        var higher = new ExecutionPolicy("higher", policy.supportedCurrencies(), new AmountLimits(CAP * 2, CAP * 2), policy.fundingByCurrency());
        assertThat(new TransferHandler(coordinator, higher, store, encoder).handle(command).storedResponse().orElseThrow().body())
                .isEqualTo(rejected.body());
        assertReconciles(source);
        assertReconciles(destination);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recipientLongOverflowIsRejectedBeforeHistoryOrBalanceWrites() throws Exception {
        var source = fundedCustomer(100);
        var destination = customer();
        var command = command(source, destination, 1);
        doAnswer(call -> {
            var actual = (Map<AccountId, LockedAccount>) call.callRealMethod();
            var result = new HashMap<>(actual);
            // Domain snapshot only: do not commit a projection without matching history.
            result.put(destination, new LockedAccount(new Account(destination,
                    AccountCategory.CUSTOMER_LIABILITY, new Money(Long.MAX_VALUE, IRR)), true));
            return Map.copyOf(result);
        }).when(store).lockAccounts(any());
        var maximum = new ExecutionPolicy("maximum", policy.supportedCurrencies(), new AmountLimits(Long.MAX_VALUE, Long.MAX_VALUE), policy.fundingByCurrency());
        var result = new TransferHandler(coordinator, maximum, store, encoder).handle(command);
        assertThat(result.outcome()).isInstanceOfSatisfying(OperationRejected.class,
                rejection -> assertThat(rejection.reason()).isEqualTo(RejectionReason.BALANCE_LIMIT_EXCEEDED));
        assertThat(balance(source)).isEqualTo(100);
        assertThat(balance(destination)).isZero();
        assertCounts(command, 1, 0, 0);
    }

    @Test
    void selfTransferIsInvalidBeforeReservationOverHttp() throws Exception {
        var source = fundedCustomer(100);
        var id = new TransactionId("transfer-self-" + UUID.randomUUID());
        var body = "{\"transactionId\":\"" + id.value() + "\",\"sourceAccountId\":\"" + source.value()
                + "\",\"destinationAccountId\":\"" + source.value() + "\",\"amountRial\":1,\"currency\":\"IRR\"}";
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + environment.getProperty("local.server.port") + "/api/v1/transfers"))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(400);
        }
        assertCounts(new TransferCommand(id, source, customer(), new Money(1, IRR)), 0, 0, 0);
        assertThat(balance(source)).isEqualTo(100);
    }

    private AccountId fundedCustomer(long amount) throws Exception {
        var customer = customer();
        credit(customer, amount);
        return customer;
    }

    private void credit(AccountId customer, long amount) {
        credits.handle(new CreditCommand(new TransactionId("transfer-fixture-credit-" + UUID.randomUUID()), customer, new Money(amount, IRR)));
    }

    private void assertTransferIntent(TransferCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT operation_type, source_account_id, destination_account_id, funding_account_id, funding_policy_version, funded_fingerprint, state "
                        + "FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("TRANSFER");
                assertThat(rows.getString(2)).isEqualToIgnoringCase(command.sourceAccountId().value().toString());
                assertThat(rows.getString(3)).isEqualToIgnoringCase(command.destinationAccountId().value().toString());
                assertThat(rows.getObject(4)).isNull();
                assertThat(rows.getObject(5)).isNull();
                assertThat(rows.getObject(6)).isNull();
                assertThat(rows.getString(7)).isEqualTo("SUCCEEDED");
            }
        }
    }

    private TransferCommand command(AccountId customer, long amount) throws Exception {
        return command(customer, customer(), amount);
    }

    private TransferCommand command(AccountId source, AccountId destination, long amount) {
        return new TransferCommand(new TransactionId("transfer-" + UUID.randomUUID()), source, destination, new Money(amount, IRR));
    }

    private AccountId customer() throws Exception {
        var id = new AccountId(UUID.randomUUID());
        createAccount(id, "CUSTOMER_LIABILITY");
        return id;
    }

    private static void createAccount(AccountId id, String category) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts (account_id,category,currency) VALUES (CAST(? AS uniqueidentifier),?,N'IRR')")) {
            statement.setString(1, id.value().toString());
            statement.setString(2, category);
            statement.executeUpdate();
        }
    }

    private HttpResponse<byte[]> post(TransferCommand command) throws Exception {
        var url = "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1/transfers";
        var body = "{\"transactionId\":\"" + command.transactionId().value() + "\",\"sourceAccountId\":\""
                + command.sourceAccountId().value() + "\",\"destinationAccountId\":\"" + command.destinationAccountId().value()
                + "\",\"amountRial\":" + command.amount().amount() + ",\"currency\":\"" + command.amount().currency().getCurrencyCode() + "\"}";
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private long balance(AccountId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT balance FROM dbo.accounts WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setQueryTimeout(2);
            statement.setString(1, id.value().toString());
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getLong(1); }
        }
    }

    private byte[] retainedBody(TransferCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT response_body FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getBytes(1); }
        }
    }

    private void assertCounts(TransferCommand command, int operations, int journals, int postings) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM dbo.operations WHERE transaction_id=?),"
                        + "(SELECT COUNT(*) FROM dbo.journals j JOIN dbo.operations o ON j.operation_id=o.operation_id WHERE o.transaction_id=?),"
                        + "(SELECT COUNT(*) FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id "
                        + "JOIN dbo.operations o ON o.operation_id=j.operation_id WHERE o.transaction_id=?)")) {
            for (int i = 1; i <= 3; i++) { statement.setString(i, command.transactionId().value()); }
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(operations);
                assertThat(result.getInt(2)).isEqualTo(journals);
                assertThat(result.getInt(3)).isEqualTo(postings);
            }
        }
    }

    private void assertReconciles(AccountId customer) throws Exception {
        assertReconciles(customer, false);
    }

    private void assertReconciles(AccountId customer, boolean debitNormal) throws Exception {
        var positiveSide = debitNormal ? "DEBIT" : "CREDIT";
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT SUM(CASE WHEN side='" + positiveSide + "' THEN CAST(amount AS decimal(38,0)) "
                        + "ELSE -CAST(amount AS decimal(38,0)) END) FROM dbo.postings WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, customer.value().toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(Long.toString(balance(customer)));
            }
        }
    }

    private ConfigurableApplicationContext independentApplication() {
        return new SpringApplicationBuilder(ConcurrentBalanceServiceApplication.class).web(WebApplicationType.NONE)
                .logStartupInfo(false).initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("explicit-transfer-test", Map.of(
                                "spring.datasource.url", System.getenv("SQLSERVER_TEST_URL"),
                                "spring.datasource.username", System.getenv("SQLSERVER_TEST_USERNAME"),
                                "spring.datasource.password", System.getenv("SQLSERVER_TEST_PASSWORD"),
                                "balance.execution.enabled", "true",
                                "balance.execution.funding[IRR].account-id", FUNDING.value().toString(),
                                "balance.execution.funding[IRR].policy-version", "credit-it-funding-v1"))))
                .run();
    }

    private void assertBalanced(TransferCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0)) ELSE 0 END),"
                        + "SUM(CASE WHEN p.side='CREDIT' THEN CAST(p.amount AS decimal(38,0)) ELSE 0 END) "
                        + "FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id JOIN dbo.operations o ON o.operation_id=j.operation_id "
                        + "WHERE o.transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(Long.toString(command.amount().amount()));
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo(result.getBigDecimal(1));
            }
        }
    }
}
