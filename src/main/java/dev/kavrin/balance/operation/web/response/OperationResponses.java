package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.web.error.ApiProblems;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.util.RawValue;

/**
 * Maps application mutation results to HTTP responses for public credit, debit and transfer
 * endpoints. Uses a retained response when available so replay does not rebuild the original
 * JSON using current response rules. Transaction completion is the application's responsibility.
 */
public final class OperationResponses {
    /** Prevents instantiation of this static response-mapping utility. */
    private OperationResponses() { }

    /**
     * Builds an HTTP response from a committed mutation outcome.
     * A retained response supplies its original status, content type and UTF-8 JSON body,
     * embedded as raw JSON rather than a quoted string. Without one, success becomes HTTP 200
     * with a {@code SUCCEEDED} body; rejection becomes a problem response enriched with the
     * transaction ID, operation type and currency.
     *
     * @param result committed outcome and currency, optionally including a retained response
     * @return the response representing the original outcome
     * @throws IllegalStateException if the outcome is an internal opening operation,
     *         even when a retained response is present
     */
    public static ResponseEntity<OperationResponseBody> from(MutationResult result) {
        if (result.outcome().operationType() == OperationType.OPENING) {
            throw new IllegalStateException("Opening is not a public operation");
        }
        if (result.storedResponse().isPresent()) {
            var response = result.storedResponse().get();
            return ResponseEntity.status(response.status()).contentType(MediaType.parseMediaType(response.contentType()))
                    .body(new StoredOperationResponse(new RawValue(new String(response.body(), StandardCharsets.UTF_8))));
        }
        // The application boundary must supply a committed result, including on replay.
        return switch (result.outcome()) {
            case OperationOutcome.Success success -> ResponseEntity.ok(new OperationResponse(
                    success.transactionId().value(), success.operationType(),
                    result.currency().getCurrencyCode(), "SUCCEEDED"));
            case OperationOutcome.Rejected rejected -> {
                var problem = new OperationProblem(ApiProblems.rejection(rejected.reason()));
                problem.setProperty("transactionId", rejected.transactionId().value());
                problem.setProperty("operationType", rejected.operationType());
                problem.setProperty("currency", result.currency().getCurrencyCode());
                yield ResponseEntity.status(problem.getStatus()).body(problem);
            }
        };
    }
}
