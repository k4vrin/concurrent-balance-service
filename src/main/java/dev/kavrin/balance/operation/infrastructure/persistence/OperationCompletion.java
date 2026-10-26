package dev.kavrin.balance.operation.infrastructure.persistence;

import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;

/** Maps every terminal outcome to its database completion fields without a downcast. */
record OperationCompletion(String state, String rejectionReason) {
    static OperationCompletion from(OperationOutcome outcome) {
        return switch (outcome) {
            case OperationSuccess success -> new OperationCompletion("SUCCEEDED", null);
            case OperationRejected rejected -> new OperationCompletion("REJECTED", rejected.reason().name());
        };
    }
}
