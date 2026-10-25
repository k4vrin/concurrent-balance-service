package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.BalanceOperations;
import dev.kavrin.balance.operation.application.CreditCommand;
import dev.kavrin.balance.operation.application.CreditHandler;
import dev.kavrin.balance.operation.application.DebitCommand;
import dev.kavrin.balance.operation.application.MutationResult;
import dev.kavrin.balance.operation.application.OperationFailure;
import dev.kavrin.balance.operation.application.TransferCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

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
    BalanceOperations stagedOperations(ObjectProvider<GetBalanceHandler> balances, ObjectProvider<CreditHandler> credits) {
        // Credit is available only with its explicit policy; other mutations retain staged fallbacks.
        return new BalanceOperations() {
            public MutationResult credit(CreditCommand command) {
                var handler = credits.getIfAvailable();
                if (handler == null) { throw unavailable(); }
                return handler.handle(command);
            }
            public MutationResult debit(DebitCommand command) { throw unavailable(); }
            public MutationResult transfer(TransferCommand command) { throw unavailable(); }
            public Money balance(AccountId accountId) {
                var handler = balances.getIfAvailable();
                if (handler == null) { throw unavailable(); }
                try {
                    return handler.handle(accountId);
                } catch (TransactionException unavailable) {
                    throw unavailable();
                }
            }
        };
    }

    private static OperationFailure unavailable() {
        return new OperationFailure(OperationFailure.Reason.UNAVAILABLE);
    }
}
