package dev.kavrin.balance.operation.web;

import org.springframework.http.ResponseEntity;
import dev.kavrin.balance.operation.application.MutationResult;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;

final class OperationResponses {
    private OperationResponses() { }

    static ResponseEntity<OperationResponseBody> from(MutationResult result) {
        if (result.outcome().operationType() == OperationType.OPENING) {
            throw new IllegalStateException("Opening is not a public operation");
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
