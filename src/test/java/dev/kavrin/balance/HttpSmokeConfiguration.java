package dev.kavrin.balance;

import dev.kavrin.balance.operation.web.BalanceController;
import dev.kavrin.balance.operation.web.configuration.HttpAdapterConfiguration;
import dev.kavrin.balance.operation.web.error.ApiExceptionHandler;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

// The infrastructure-free smoke test assembles only its web boundary, never the production component scan.
/**
 * Assembles only the HTTP boundary for the explicitly selected infrastructure-free smoke tests.
 *
 * <p>The smoke profile imports parsing, controller and advice components without the production
 * component scan. It provides no persistence or mutation substitute and does not alter normal startup.
 */
@Configuration(proxyBeanMethods = false)
@Profile("smoke")
@EnableAutoConfiguration
@Import({HttpAdapterConfiguration.class, BalanceController.class, ApiExceptionHandler.class})
class HttpSmokeConfiguration {
}
