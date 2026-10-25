package dev.kavrin.balance;

import dev.kavrin.balance.operation.web.ApiExceptionHandler;
import dev.kavrin.balance.operation.web.BalanceController;
import dev.kavrin.balance.operation.web.HttpAdapterConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Import;

// The infrastructure-free smoke test assembles only its web boundary, never the production component scan.
@Configuration(proxyBeanMethods = false)
@Profile("smoke")
@EnableAutoConfiguration
@Import({HttpAdapterConfiguration.class, BalanceController.class, ApiExceptionHandler.class})
class HttpSmokeConfiguration {
}
