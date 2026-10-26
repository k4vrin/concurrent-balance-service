package dev.kavrin.balance.operation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OperationOutcomeTests {

    private static final TransactionId TRANSACTION_ID = new TransactionId("operation-001");

    @ParameterizedTest
    @EnumSource(OperationType.class)
    void successPreservesOperationIdentity(OperationType type) {
        OperationOutcome outcome = new OperationSuccess(TRANSACTION_ID, type);

        assertThat(outcome.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(outcome.operationType()).isEqualTo(type);
    }

    @ParameterizedTest
    @EnumSource(RejectionReason.class)
    void rejectionPreservesBusinessReason(RejectionReason reason) {
        var rejection = new OperationRejected(TRANSACTION_ID, OperationType.DEBIT, reason);

        assertThat(rejection.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(rejection.operationType()).isEqualTo(OperationType.DEBIT);
        assertThat(rejection.reason()).isEqualTo(reason);
    }

    @Test
    void successRequiresIdentityAndType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OperationSuccess(null, OperationType.CREDIT));
        assertThatNullPointerException()
                .isThrownBy(() -> new OperationSuccess(TRANSACTION_ID, null));
    }

    @Test
    void rejectionRequiresIdentityTypeAndReason() {
        assertThatNullPointerException().isThrownBy(() ->
                new OperationRejected(null, OperationType.DEBIT, RejectionReason.INSUFFICIENT_FUNDS));
        assertThatNullPointerException().isThrownBy(() ->
                new OperationRejected(TRANSACTION_ID, null, RejectionReason.INSUFFICIENT_FUNDS));
        assertThatNullPointerException().isThrownBy(() ->
                new OperationRejected(TRANSACTION_ID, OperationType.DEBIT, null));
    }

    @Test
    void businessRejectionIsReturnedAsValue() {
        OperationOutcome outcome = insufficientFunds();

        assertThat(outcome).isEqualTo(new OperationRejected(
                TRANSACTION_ID, OperationType.DEBIT, RejectionReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void rejectionReasonsRemainDistinct() {
        var insufficientFunds = insufficientFunds();
        var overflow = new OperationRejected(
                TRANSACTION_ID, OperationType.DEBIT, RejectionReason.BALANCE_OVERFLOW);

        assertThat(insufficientFunds).isNotEqualTo(overflow);
    }

    @Test
    void matchingIdentityDoesNotEquateSuccessAndRejection() {
        OperationOutcome success = new OperationSuccess(TRANSACTION_ID, OperationType.DEBIT);

        assertThat(success).isNotEqualTo(insufficientFunds());
    }

    @Test
    void dispatchesExhaustivelyByBusinessOutcome() {
        OperationOutcome success = new OperationSuccess(TRANSACTION_ID, OperationType.DEBIT);

        assertThat(describe(success)).isEqualTo("success");
        assertThat(describe(insufficientFunds())).isEqualTo("rejected: INSUFFICIENT_FUNDS");
    }

    private OperationRejected insufficientFunds() {
        return new OperationRejected(
                TRANSACTION_ID, OperationType.DEBIT, RejectionReason.INSUFFICIENT_FUNDS);
    }

    private String describe(OperationOutcome outcome) {
        return switch (outcome) {
            case OperationSuccess success -> "success";
            case OperationRejected rejected -> "rejected: " + rejected.reason();
        };
    }
}
