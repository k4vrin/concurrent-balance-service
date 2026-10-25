package dev.kavrin.balance;

import dev.kavrin.balance.operation.web.BalanceController;
import dev.kavrin.balance.operation.web.configuration.HttpAdapterConfiguration;
import dev.kavrin.balance.operation.web.error.ApiExceptionHandler;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

// The infrastructure-free smoke test assembles only its web boundary, never the production component scan.
@Configuration(proxyBeanMethods = false)
@Profile("smoke")
@EnableAutoConfiguration
@Import({HttpAdapterConfiguration.class, BalanceController.class, ApiExceptionHandler.class})
class HttpSmokeConfiguration {
}
