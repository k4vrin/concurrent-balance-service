package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.application.transfer.TransferHandler;
import dev.kavrin.balance.operation.domain.*;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;
import dev.kavrin.balance.operation.web.configuration.HttpAdapterConfiguration;
import java.util.Currency;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BalanceController.class)
@Import(HttpAdapterConfiguration.class)
class BalanceControllerTests {
    private static final String SOURCE = "00000000-0000-0000-0000-000000000001";
    private static final String DESTINATION = "00000000-0000-0000-0000-000000000002";
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final String CREDIT = "/api/v1/accounts/" + SOURCE + "/credits";
    private static final String BODY = "{\"transactionId\":\"Tx-001\",\"amountRial\":100,\"currency\":\"IRR\"}";

    @Autowired MockMvc http;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    @MockitoBean CreditHandler credits;
    @MockitoBean DebitHandler debits;
    @MockitoBean TransferHandler transfers;
    @MockitoBean GetBalanceHandler balances;

    @Test
    void creditPreservesIdentityAndRialUnits() throws Exception {
        when(credits.handle(any())).thenReturn(success(OperationType.CREDIT));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").value("Tx-001"))
                .andExpect(jsonPath("$.operationType").value("CREDIT"))
                .andExpect(jsonPath("$.currency").value("IRR"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.balanceRial").doesNotExist());
        verify(credits).handle(new CreditCommand(new TransactionId("Tx-001"), account(SOURCE), new Money(100, IRR)));
    }

    @Test
    void debitUsesItsOwnCommand() throws Exception {
        when(debits.handle(any())).thenReturn(success(OperationType.DEBIT));
        http.perform(post(CREDIT.replace("credits", "debits")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationType").value("DEBIT"));
        verify(debits).handle(new DebitCommand(new TransactionId("Tx-001"), account(SOURCE), new Money(100, IRR)));
    }

    @Test
    void transferPreservesDirection() throws Exception {
        when(transfers.handle(any())).thenReturn(success(OperationType.TRANSFER));
        http.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(transferBody(DESTINATION)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.operationType").value("TRANSFER"));
        verify(transfers).handle(new TransferCommand(new TransactionId("Tx-001"), account(SOURCE),
                account(DESTINATION), new Money(100, IRR)));
    }

    @Test
    void balanceMapsApplicationSnapshot() throws Exception {
        when(balances.handle(account(SOURCE))).thenReturn(new Money(150000, IRR));
        http.perform(get("/api/v1/accounts/" + SOURCE + "/balance"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(SOURCE))
                .andExpect(jsonPath("$.balanceRial").value(150000)).andExpect(jsonPath("$.currency").value("IRR"));
    }

    static Stream<String> invalidBodies() {
        return Stream.of("", "{", "null", "[]", "{}", BODY + " {}",
                BODY.replace("100", "1.5"), BODY.replace("100", "1.0"), BODY.replace("100", "1e2"),
                BODY.replace("100", "\"100\""), BODY.replace("100", "true"), BODY.replace("100", "null"),
                BODY.replace("100", "0"), BODY.replace("100", "-1"),
                BODY.replace("100", "9223372036854775808"), BODY.replace("100", "-9223372036854775809"),
                BODY.replace("\"Tx-001\"", "123"), BODY.replace("Tx-001", " Tx-001"),
                BODY.replace("Tx-001", "a".repeat(129)), BODY.replace("Tx-001", "شناسه"),
                BODY.replace("\"IRR\"", "123"), BODY.replace("IRR", "irr"), BODY.replace("IRR", "ZZZ"),
                BODY.replace("\"currency\":\"IRR\"", "\"currency\":null"),
                BODY.replace("\"amountRial\":100,", ""), BODY.replace("\"transactionId\":\"Tx-001\",", ""),
                BODY.replace("}", ",\"extra\":1}"), BODY.replace("}", ",\"amountRial\":200}"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidInputBeforeApplicationCall(String body) throws Exception {
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(credits, debits, transfers, balances);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-1-1-1-1", "not-a-uuid", "00000000000000000000000000000001"})
    void rejectsNoncanonicalUuidShape(String id) throws Exception {
        http.perform(get("/api/v1/accounts/" + id + "/balance"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(credits, debits, transfers, balances);
    }

    @Test
    void rejectsUuidEquivalentSelfTransfer() throws Exception {
        http.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON).content(transferBody(SOURCE)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(credits, debits, transfers, balances);
    }

    @Test
    void acceptsMaximumRepresentableAmountWithoutScaling() throws Exception {
        when(credits.handle(any())).thenReturn(success(OperationType.CREDIT));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("100", "9223372036854775807")))
                .andExpect(status().isOk());
        verify(credits).handle(new CreditCommand(new TransactionId("Tx-001"), account(SOURCE), new Money(Long.MAX_VALUE, IRR)));
    }

    @Test
    void leavesSupportedCurrencyPolicyToReplayAwareApplication() throws Exception {
        when(credits.handle(any())).thenThrow(new OperationFailure(OperationFailureReason.UNSUPPORTED_CURRENCY));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY.replace("IRR", "USD")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        verify(credits).handle(new CreditCommand(new TransactionId("Tx-001"), account(SOURCE),
                new Money(100, Currency.getInstance("USD"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNT_NOT_FOUND", "INSUFFICIENT_FUNDS", "BALANCE_OVERFLOW", "TRANSACTION_LIMIT_EXCEEDED", "BALANCE_LIMIT_EXCEEDED"})
    void mapsCommittedBusinessRejections(String reason) throws Exception {
        var rejection = new OperationRejected(new TransactionId("Tx-001"), OperationType.CREDIT,
                RejectionReason.valueOf(reason));
        when(credits.handle(any())).thenReturn(new MutationResult(rejection, IRR));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(reason.equals("ACCOUNT_NOT_FOUND") ? 404 : 409))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.status").value(reason.equals("ACCOUNT_NOT_FOUND") ? 404 : 409))
                .andExpect(jsonPath("$.title").value(reason.equals("ACCOUNT_NOT_FOUND") ? "Not Found" : "Conflict"))
                .andExpect(jsonPath("$.instance").value(CREDIT))
                .andExpect(jsonPath("$.properties").doesNotExist())
                .andExpect(jsonPath("$.code").value(reason)).andExpect(jsonPath("$.transactionId").value("Tx-001"))
                .andExpect(jsonPath("$.operationType").value("CREDIT")).andExpect(jsonPath("$.currency").value("IRR"));
    }

    @Test
    void mapsIdentityConflictWithoutReplacementOutcome() throws Exception {
        when(credits.handle(any())).thenThrow(new OperationFailure(OperationFailureReason.TRANSACTION_ID_CONFLICT));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRANSACTION_ID_CONFLICT"));
    }

    @Test
    void missingBalanceIsNotReturnedAsZero() throws Exception {
        when(balances.handle(any())).thenThrow(new OperationFailure(OperationFailureReason.ACCOUNT_NOT_FOUND));
        http.perform(get("/api/v1/accounts/" + SOURCE + "/balance"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void balanceTransactionFailureMapsToTemporaryUnavailability() throws Exception {
        when(balances.handle(any())).thenThrow(new TransactionSystemException("read transaction failed"));
        http.perform(get("/api/v1/accounts/" + SOURCE + "/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OPERATION_UNAVAILABLE"));
    }

    @Test
    void unavailableOutcomePreservesUncertainCommitGuidance() throws Exception {
        when(credits.handle(any())).thenThrow(new OperationFailure(OperationFailureReason.UNAVAILABLE));
        http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OPERATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("same transaction ID")));
    }

    @Test
    void unexpectedDefectsStayInternalAndDoNotBecomeBusinessRejections() throws Exception {
        when(credits.handle(any())).thenThrow(new IllegalArgumentException("secret SQL password"));
        var response = http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("secret", "SQL", "password", "stackTrace");
    }

    @Test
    void adapterSerializesSameSuppliedOutcomeIdenticallyOnRetry() throws Exception {
        when(credits.handle(any())).thenReturn(success(OperationType.CREDIT));
        var first = http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse();
        var retry = http.perform(post(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse();
        assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
        verify(credits, times(2)).handle(any()); // Durable deduplication belongs behind this boundary.
    }

    @Test
    void rejectsNonJsonMedia() throws Exception {
        http.perform(post(CREDIT).contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        verifyNoInteractions(credits, debits, transfers, balances);
    }

    @Test
    void unsupportedMethodHasStableProblem() throws Exception {
        http.perform(put(CREDIT).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(credits, debits, transfers, balances);
    }

    @Test
    void unsupportedAcceptHasStableProblem() throws Exception {
        when(balances.handle(any())).thenReturn(new Money(100, IRR));
        http.perform(get("/api/v1/accounts/" + SOURCE + "/balance").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    private static MutationResult success(OperationType type) {
        return new MutationResult(new OperationSuccess(new TransactionId("Tx-001"), type), IRR);
    }

    private static AccountId account(String id) { return new AccountId(UUID.fromString(id)); }

    private static String transferBody(String destination) {
        return BODY.replace("}", ",\"sourceAccountId\":\"" + SOURCE + "\",\"destinationAccountId\":\"" + destination + "\"}");
    }
}
