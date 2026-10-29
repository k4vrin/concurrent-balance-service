package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the immutable financial policy only when mutation execution is explicitly enabled.
 *
 * <p>Typed external settings are converted through domain validation so incomplete funding or cap
 * configuration fails startup. Disabled execution does not require a fabricated policy snapshot.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ExecutionPolicyProperties.class)
public class ExecutionPolicyConfiguration {
    @Bean
    ExecutionPolicy executionPolicy(ExecutionPolicyProperties properties) {
        return properties.toPolicy();
    }
}
