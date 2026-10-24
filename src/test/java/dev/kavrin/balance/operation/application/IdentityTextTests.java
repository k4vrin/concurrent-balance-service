package dev.kavrin.balance.operation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class IdentityTextTests {
    @ParameterizedTest
    @ValueSource(strings = {"execution-v1", " actor ", "سیاست-۱", "actor-\uD83D\uDE00"})
    void acceptsNonblankWellFormedUnicodeWithoutRestrictingItToAscii(String text) {
        assertThatCode(() -> IdentityText.requireValid(text)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\uD800", "\uDC00", "actor-\uD800-x"})
    void rejectsBlankOrMalformedIdentityText(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> IdentityText.requireValid(text));
    }

    @Test
    void rejectsMissingIdentityText() {
        assertThatNullPointerException().isThrownBy(() -> IdentityText.requireValid(null));
    }
}
