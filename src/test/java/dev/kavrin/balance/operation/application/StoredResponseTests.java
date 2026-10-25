package dev.kavrin.balance.operation.application;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StoredResponseTests {
    @Test
    void retainsOriginalBytesWithoutSharingMutableArrays() {
        var bytes = "{}".getBytes(StandardCharsets.UTF_8);
        var response = new StoredResponse(1, 200, "application/json", bytes);
        bytes[0] = '[';
        response.body()[1] = ']';
        assertThat(response.body()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void comparesResponseBytesByValueAcrossIndependentLoads() {
        var original = new StoredResponse(1, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
        var loaded = new StoredResponse(1, 200, "application/json", original.body());
        assertThat(loaded).isEqualTo(original).hasSameHashCodeAs(original);
    }

    @Test
    void rejectsUnknownFormatsAndIncompatibleStatusOrMediaTypes() {
        var body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredResponse(2, 200, "application/json", body));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredResponse(1, 503, "application/problem+json", body));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredResponse(1, 200, "application/problem+json", body));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredResponse(1, 409, "application/json", body));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredResponse(1, 200, "application/json", new byte[0]));
    }
}
