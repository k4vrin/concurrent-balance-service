package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTransferResponseEncoderTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private final JsonTransferResponseEncoder encoder = new JsonTransferResponseEncoder(json);
    private final TransactionId id = new TransactionId("transfer-101");

    @Test
    void acceptsOriginalSuccessMetadata() {
        assertThatCode(() -> encoder.validate(success(body()))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"transactionId", "operationType", "currency", "status"})
    void failsClosedWhenResponseIdentityOrOutcomeDisagreesWithTheOperation(String field) {
        var body = new HashMap<>(body());
        body.put(field, "different");

        assertThatThrownBy(() -> encoder.validate(success(body))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedStoredJsonCannotBecomeAReplay() {
        var invalid = new StoredResponse(1, 200, "application/json", "not-json".getBytes(StandardCharsets.UTF_8));
        var result = new MutationResult(new OperationSuccess(id, OperationType.TRANSFER), Currency.getInstance("IRR"), Optional.of(invalid));

        assertThatThrownBy(() -> encoder.validate(result)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void failsClosedWhenTheRejectionReasonDisagrees() {
        var bytes = json.writeValueAsBytes(Map.of("transactionId", id.value(), "operationType", "TRANSFER", "currency", "IRR",
                "status", 409, "code", "ACCOUNT_NOT_FOUND"));
        var result = new MutationResult(new OperationRejected(id, OperationType.TRANSFER, RejectionReason.TRANSACTION_LIMIT_EXCEEDED),
                Currency.getInstance("IRR"), Optional.of(new StoredResponse(1, 409, "application/problem+json", bytes)));

        assertThatThrownBy(() -> encoder.validate(result)).isInstanceOf(IllegalStateException.class);
    }

    private Map<String, String> body() {
        return Map.of("transactionId", id.value(), "operationType", "TRANSFER", "currency", "IRR", "status", "SUCCEEDED");
    }

    private MutationResult success(Map<String, String> body) {
        return new MutationResult(new OperationSuccess(id, OperationType.TRANSFER), Currency.getInstance("IRR"),
                Optional.of(new StoredResponse(1, 200, "application/json", json.writeValueAsBytes(body))));
    }
}
