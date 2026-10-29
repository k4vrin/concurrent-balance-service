package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.transfer.TransferHandler;
import dev.kavrin.balance.operation.domain.OperationRejected;
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

/**
 * Checks typed mutation response mapping and Spring-compatible business-problem serialization.
 *
 * <p>Cases retain route, operation identity and business reason without executing financial transactions.
 */
@WebMvcTest(BalanceController.class)
@Import(HttpAdapterConfiguration.class)
class OperationResponsesTests {
    private static final Currency IRR = Currency.getInstance("IRR");
    @Autowired JsonMapper json;
    @MockitoBean CreditHandler credits;
    @MockitoBean DebitHandler debits;
    @MockitoBean TransferHandler transfers;
    @MockitoBean GetBalanceHandler balances;

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
        var rejected = new MutationResult(new OperationRejected(id, OperationType.DEBIT,
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

    @Test
    void encodesTransferRejectionWithItsOriginalRouteAndIdentity() {
        var encoder = new JsonTransferResponseEncoder(json);
        var id = new TransactionId("transfer-encode-001");
        var command = new TransferCommand(id, new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()), new Money(101, IRR));
        var rejected = new MutationResult(new OperationRejected(id, OperationType.TRANSFER,
                RejectionReason.BALANCE_LIMIT_EXCEEDED), IRR);
        var response = encoder.encode(command, rejected);
        Map<?, ?> body = json.readValue(response.body(), Map.class);
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.contentType()).isEqualTo("application/problem+json");
        assertThat(body.get("instance")).isEqualTo("/api/v1/transfers");
        assertThat(body.get("operationType")).isEqualTo("TRANSFER");
        assertThat(body.get("code")).isEqualTo("BALANCE_LIMIT_EXCEEDED");
        encoder.validate(new MutationResult(rejected.outcome(), IRR, Optional.of(response)));
    }

}
