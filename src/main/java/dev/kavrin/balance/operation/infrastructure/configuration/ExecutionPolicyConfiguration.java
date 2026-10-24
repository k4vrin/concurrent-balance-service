package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.operation.application.ExecutionPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!smoke")
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ExecutionPolicyProperties.class)
public class ExecutionPolicyConfiguration {
    @Bean
    ExecutionPolicy executionPolicy(ExecutionPolicyProperties properties) {
        return properties.toPolicy();
    }
}
