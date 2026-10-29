package dev.kavrin.balance.account.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks required UUID account identity and value semantics without HTTP parsing or persistence.
 */
class AccountIdTests {

    @Test
    void rejectsMissingIdentifier() {
        assertThatNullPointerException().isThrownBy(() -> new AccountId(null));
    }

    @Test
    void preservesUuidIdentity() {
        var value = UUID.fromString("7f000000-0000-4000-8000-000000000042");

        var id = new AccountId(value);

        assertThat(id.value()).isEqualTo(value);
        assertThat(id).isEqualTo(new AccountId(UUID.fromString(value.toString())));
    }
}
