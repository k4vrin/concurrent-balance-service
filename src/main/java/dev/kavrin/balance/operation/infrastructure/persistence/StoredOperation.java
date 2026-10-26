package dev.kavrin.balance.operation.infrastructure.persistence;

import dev.kavrin.balance.operation.application.identity.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import jakarta.persistence.Tuple;
import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;

/** Package-local persisted fields; check full canonical intent before decoding the retained result. */
record StoredOperation(int canonicalVersion, byte[] canonicalRequest, String operationType,
        String currency, String state, String rejectionReason, Number responseVersion, Number httpStatus,
        String contentType, byte[] responseBody) {
    static StoredOperation from(Tuple row) {
        return new StoredOperation(
                row.get("canonical_format_version", Number.class).intValue(),
                row.get("canonical_request", byte[].class), row.get("operation_type", String.class),
                row.get("currency", String.class), row.get("state", String.class),
                row.get("rejection_reason", String.class), row.get("response_format_version", Number.class),
                row.get("http_status", Number.class), row.get("response_content_type", String.class),
                row.get("response_body", byte[].class));
    }

    void requireMatching(CanonicalFingerprint canonical) {
        if (canonicalVersion != canonical.version()) {
            throw new IllegalStateException("Unsupported stored canonical version");
        }
        if (!Arrays.equals(canonicalRequest, canonical.bytes())) {
            throw new OperationFailure(OperationFailureReason.TRANSACTION_ID_CONFLICT);
        }
    }

    MutationResult toResult(TransactionId transactionId) {
        var type = OperationType.valueOf(operationType);
        OperationOutcome outcome = switch (state) {
            case "SUCCEEDED" -> new OperationSuccess(transactionId, type);
            case "REJECTED" -> new OperationRejected(transactionId, type, RejectionReason.valueOf(rejectionReason));
            default -> throw new IllegalStateException("Committed operation has no terminal outcome");
        };
        var response = new StoredResponse(responseVersion.intValue(), httpStatus.intValue(), contentType, responseBody);
        return new MutationResult(outcome, Currency.getInstance(currency), Optional.of(response));
    }
}
