package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.BalanceOperations;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.operation.web.BalanceController;
import dev.kavrin.balance.operation.web.configuration.HttpAdapterConfiguration;
import dev.kavrin.balance.operation.web.error.ApiProblems;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(BalanceController.class)
@Import(HttpAdapterConfiguration.class)
class OperationResponsesTests {
    private static final Currency IRR = Currency.getInstance("IRR");
    @Autowired JsonMapper json;
    @MockitoBean BalanceOperations operations;

    @Test
    void typedProblemPreservesSpringProblemSerialization() {
        var original = ApiProblems.rejection(RejectionReason.INSUFFICIENT_FUNDS);
        original.setProperty("transactionId", "Tx-001");
        original.setProperty("operationType", OperationType.DEBIT);
        original.setProperty("currency", IRR.getCurrencyCode());
        var typed = new OperationProblem(original);

        assertThat(json.writeValueAsString(typed)).isEqualTo(json.writeValueAsString(original));
    }
    @Test
    void encodesTheDebitRouteAndTerminalInsufficientFundsProblem() {
        var encoder = new JsonDebitResponseEncoder(json);
        var id = new TransactionId("debit-encode-001");
        var account = new AccountId(UUID.randomUUID());
        var command = new DebitCommand(
                id, account, new Money(101, Currency.getInstance("IRR")));
        var rejected = new MutationResult(new OperationOutcome.Rejected(id, OperationType.DEBIT,
                RejectionReason.INSUFFICIENT_FUNDS), Currency.getInstance("IRR"));

        var response = encoder.encode(command, rejected);
        Map<?, ?> body = json.readValue(response.body(), Map.class);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.contentType()).isEqualTo("application/problem+json");
        assertThat(body.get("instance")).isEqualTo("/api/v1/accounts/" + account.value() + "/debits");
        assertThat(body.get("operationType")).isEqualTo("DEBIT");
        assertThat(body.get("code")).isEqualTo("INSUFFICIENT_FUNDS");
        encoder.validate(new MutationResult(rejected.outcome(), rejected.currency(), Optional.of(response)));
    }

}
