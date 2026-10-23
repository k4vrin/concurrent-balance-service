package dev.kavrin.balance.operation.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.application.BalanceOperations;
import dev.kavrin.balance.operation.application.CreditCommand;
import dev.kavrin.balance.operation.application.DebitCommand;
import dev.kavrin.balance.operation.application.TransferCommand;
import dev.kavrin.balance.operation.application.MutationResult;
import dev.kavrin.balance.operation.application.OperationFailure;
import dev.kavrin.balance.money.Money;

@Configuration(proxyBeanMethods = false)
public class HttpAdapterConfiguration {
    @Bean
    JsonMapperBuilderCustomizer strictRequests() {
        return builder -> builder
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Bean
    @ConditionalOnMissingBean(BalanceOperations.class)
    BalanceOperations unavailableOperations() {
        // Keep routes reviewable before CBS10–15 supplies durable handlers; never invent financial success.
        return new BalanceOperations() {
            public MutationResult credit(CreditCommand command) { throw unavailable(); }
            public MutationResult debit(DebitCommand command) { throw unavailable(); }
            public MutationResult transfer(TransferCommand command) { throw unavailable(); }
            public Money balance(AccountId accountId) { throw unavailable(); }
        };
    }

    private static OperationFailure unavailable() {
        return new OperationFailure(OperationFailure.Reason.UNAVAILABLE);
    }
}
