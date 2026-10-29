package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.operation.domain.OperationType;

/**
 * Carries the minimal public success acknowledgement for a committed mutation.
 *
 * <p>Balances and persistence internals are omitted; authoritative balance lookup is a separate API.
 * The application transaction must complete before this acknowledgement is exposed.
 *
 * @param transactionId retained retry identity
 * @param operationType successful mutation type
 * @param currency operation currency code
 * @param status success status rendered by the response mapper
 */
public record OperationResponse(String transactionId, OperationType operationType,
        String currency, String status) implements OperationResponseBody {
}
