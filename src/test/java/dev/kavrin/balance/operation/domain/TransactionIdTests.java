package dev.kavrin.balance.operation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TransactionIdTests {

    @ParameterizedTest
    @ValueSource(strings = {"a", "0", "transfer-001", "A_Z:9.x-y",
            "10000000-0000-4000-8000-000000000001"})
    void preservesValidIdentifierExactly(String value) {
        assertThat(new TransactionId(value).value()).isEqualTo(value);
    }

    @Test
    void acceptsMaximumLength() {
        String value = "a".repeat(128);

        assertThat(new TransactionId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " transfer-001", "transfer-001 ", "transfer 001",
            "\ttransfer-001", "transfer-001\n", "transfer/001", "transfer+001",
            "café", "انتقال-001", "transfer\u200b001", "transfer-001\u00a0"})
    void rejectsInvalidIdentifierWithoutNormalizing(String value) {
        // Silently trimming or rewriting a key would change the client's retry identity.
        assertThatIllegalArgumentException().isThrownBy(() -> new TransactionId(value));
    }

    @Test
    void rejectsOversizedIdentifier() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TransactionId("a".repeat(129)));
    }

    @Test
    void rejectsMissingIdentifier() {
        assertThatNullPointerException().isThrownBy(() -> new TransactionId(null));
    }

    @Test
    void sameValueHasSameIdentity() {
        var first = new TransactionId("transfer-001");
        var retry = new TransactionId("transfer-001");

        assertThat(first).isEqualTo(retry);
        assertThat(first.hashCode()).isEqualTo(retry.hashCode());
    }

    @Test
    void caseRemainsPartOfIdentity() {
        assertThat(new TransactionId("Transfer-001"))
                .isNotEqualTo(new TransactionId("transfer-001"));
    }
}
