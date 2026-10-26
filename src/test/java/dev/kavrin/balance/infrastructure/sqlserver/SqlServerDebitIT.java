package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.ConcurrentBalanceServiceApplication;
import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.debit.DebitResponseEncoder;
import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.application.policy.AmountLimits;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.SupportedCurrencies;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaDebitStore;
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
class SqlServerDebitIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final long CAP = 10_000_000_000_000L;
    private static final AccountId FUNDING = new AccountId(UUID.randomUUID());
    @Autowired DebitHandler handler;
    @Autowired CreditHandler credits;
    @Autowired OperationCoordinator coordinator;
    @Autowired DebitResponseEncoder encoder;
    @Autowired ExecutionPolicy policy;
    @Autowired Environment environment;
    @MockitoSpyBean JpaDebitStore store;

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
    void exactFundsHttpDebitCommitsBothProjectionsAndReplaysWithoutSubtractingAgain() throws Exception {
        var customer = fundedCustomer(100);
        long fundingBefore = balance(FUNDING);
        var command = command(customer, 100);
        var first = post(command);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(first.body()).isEqualTo(retainedBody(command));
        assertThat(balance(customer)).isZero();
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore - 100);
        assertThat(post(command).body()).isEqualTo(first.body());
        assertThat(balance(customer)).isZero();
        assertCounts(command, 1, 1, 2);
        assertBalanced(command);
        assertReconciles(customer);
        assertReconciles(FUNDING, true);
        assertDebitIntent(command);
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + environment.getProperty("local.server.port") + "/api/v1/accounts/"
                    + customer.value() + "/balance")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"balanceRial\":0");
        }
    }

    @Test
    void oneRialOverAvailableFundsIsRetainedAfterLaterCredit() throws Exception {
        var customer = fundedCustomer(100);
        long fundingBefore = balance(FUNDING);
        var command = command(customer, 101);
        var rejected = post(command);

        assertThat(rejected.statusCode()).isEqualTo(409);
        assertThat(rejected.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(new String(rejected.body(), StandardCharsets.UTF_8)).contains("INSUFFICIENT_FUNDS");
        assertThat(rejected.body()).isEqualTo(retainedBody(command));
        assertThat(balance(customer)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        assertCounts(command, 1, 0, 0);

        credit(customer, 200);
        var replay = post(command);
        assertThat(replay.statusCode()).isEqualTo(409);
        assertThat(replay.body()).isEqualTo(rejected.body());
        assertThat(balance(customer)).isEqualTo(300);
        assertCounts(command, 1, 0, 0);
        assertThat(post(command(customer, 101)).statusCode()).isEqualTo(200);
        assertThat(balance(customer)).isEqualTo(199);
        assertReconciles(customer);
        assertReconciles(FUNDING, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "account", "operation"})
    void conflictingReuseNeverAddsAnotherFinancialEffect(String changed) throws Exception {
        var customer = fundedCustomer(100);
        var original = command(customer, 40);
        assertThat(post(original).statusCode()).isEqualTo(200);
        switch (changed) {
            case "amount" -> assertThat(post(new DebitCommand(original.transactionId(), customer, new Money(41, IRR))).statusCode()).isEqualTo(409);
            case "account" -> assertThat(post(new DebitCommand(original.transactionId(), customer(), original.amount())).statusCode()).isEqualTo(409);
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
            var fresh = first.getBean(DebitHandler.class);
            originalSuccess = fresh.handle(success).storedResponse().orElseThrow().body();
            originalRejection = fresh.handle(rejected).storedResponse().orElseThrow().body();
        }
        credit(customer, 200);
        try (var second = independentApplication()) {
            var fresh = second.getBean(DebitHandler.class);
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
        var fresh = new DebitHandler(coordinator, disabled, store, encoder);
        assertThat(fresh.handle(command).storedResponse().orElseThrow().body()).isEqualTo(original.storedResponse().orElseThrow().body());
        assertCounts(command, 1, 0, 0);
        assertThat(balance(command.accountId())).isEqualTo(100);
    }

    @Test
    void missingAccountRejectionStaysTerminalAfterAccountCreation() throws Exception {
        var id = new AccountId(UUID.randomUUID());
        var command = command(id, 1);
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
        var fresh = new DebitHandler(coordinator, higher, store, encoder);
        assertThat(fresh.handle(command).storedResponse().orElseThrow().body()).isEqualTo(original.body());
        assertCounts(command, 1, 0, 0);
        assertThat(balance(command.accountId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal", "projections", "outcome"})
    void failureAfterEachWriteRollsBackHistoryBothBalancesAndIdentity(String boundary) throws Exception {
        var customer = fundedCustomer(100);
        var command = command(customer, 40);
        long fundingBefore = balance(FUNDING);
        switch (boundary) {
            case "journal" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected debit failure"); })
                    .when(store).appendJournal(any(), any());
            case "projections" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected debit failure"); })
                    .when(store).applyProjections(any());
            case "outcome" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected debit failure"); })
                    .when(store).complete(any(), any(), any());
            default -> throw new AssertionError(boundary);
        }
        assertThat(post(command).statusCode()).isEqualTo(503);
        assertCounts(command, 0, 0, 0);
        assertThat(balance(customer)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        org.mockito.Mockito.reset(store);
        assertThat(post(command).statusCode()).isEqualTo(200);
        assertCounts(command, 1, 1, 2);
        assertThat(balance(customer)).isEqualTo(60);
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
    @SuppressWarnings("unchecked")
    void fundingUnderflowRollsBackTheReservationBeforeAnyHistoryWrite() throws Exception {
        var customer = fundedCustomer(100);
        var command = command(customer, 40);
        long fundingBefore = balance(FUNDING);
        // Inject a domain snapshot only; never commit an unreconciled financial fixture.
        doAnswer(call -> {
            var actual = (Map<AccountId, LockedAccount>) call.callRealMethod();
            var result = new HashMap<>(actual);
            result.put(FUNDING, new LockedAccount(
                    new Account(FUNDING, AccountCategory.FUNDING_CLEARING, new Money(Long.MIN_VALUE, IRR)), true));
            return Map.copyOf(result);
        }).when(store).lockAccounts(any());
        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ArithmeticException.class);
        assertCounts(command, 0, 0, 0);
        assertThat(balance(customer)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
    }

    private AccountId fundedCustomer(long amount) throws Exception {
        var customer = customer();
        credit(customer, amount);
        return customer;
    }

    private void credit(AccountId customer, long amount) {
        credits.handle(new CreditCommand(new TransactionId("debit-fixture-credit-" + UUID.randomUUID()), customer, new Money(amount, IRR)));
    }

    private void assertDebitIntent(DebitCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT operation_type, source_account_id, destination_account_id, state "
                        + "FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("DEBIT");
                assertThat(rows.getString(2)).isEqualToIgnoringCase(command.accountId().value().toString());
                assertThat(rows.getObject(3)).isNull();
                assertThat(rows.getString(4)).isEqualTo("SUCCEEDED");
            }
        }
    }

    private DebitCommand command(AccountId customer, long amount) {
        return new DebitCommand(new TransactionId("debit-" + UUID.randomUUID()), customer, new Money(amount, IRR));
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

    private HttpResponse<byte[]> post(DebitCommand command) throws Exception {
        var url = "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1/accounts/" + command.accountId().value() + "/debits";
        var body = "{\"transactionId\":\"" + command.transactionId().value() + "\",\"amountRial\":" + command.amount().amount()
                + ",\"currency\":\"" + command.amount().currency().getCurrencyCode() + "\"}";
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

    private byte[] retainedBody(DebitCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT response_body FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getBytes(1); }
        }
    }

    private void assertCounts(DebitCommand command, int operations, int journals, int postings) throws Exception {
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
                        new MapPropertySource("explicit-debit-test", Map.of(
                                "spring.datasource.url", System.getenv("SQLSERVER_TEST_URL"),
                                "spring.datasource.username", System.getenv("SQLSERVER_TEST_USERNAME"),
                                "spring.datasource.password", System.getenv("SQLSERVER_TEST_PASSWORD"),
                                "balance.execution.enabled", "true",
                                "balance.execution.funding[IRR].account-id", FUNDING.value().toString(),
                                "balance.execution.funding[IRR].policy-version", "credit-it-funding-v1"))))
                .run();
    }

    private void assertBalanced(DebitCommand command) throws Exception {
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
