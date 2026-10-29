package dev.kavrin.balance.operation.web.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

/**
 * Configures the shared strict JSON boundary used by the balance HTTP API.
 *
 * <p>Parsing rules keep malformed or ambiguous request data out of application commands. This
 * configuration supplies no financial handler, policy snapshot or persistence substitute.
 */
@Configuration(proxyBeanMethods = false)
public class HttpAdapterConfiguration {
    @Bean
    JsonMapperBuilderCustomizer strictRequests() {
        return builder -> builder
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

}
