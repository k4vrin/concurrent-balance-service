package dev.kavrin.balance.operation.web.configuration;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.BalanceOperations;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
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
    BalanceOperations stagedOperations(ObjectProvider<GetBalanceHandler> balances, ObjectProvider<CreditHandler> credits,
            ObjectProvider<DebitHandler> debits) {
        // Funded mutations require explicit execution policy; transfer remains staged until CBS14.
        return new BalanceOperations() {
            public MutationResult credit(CreditCommand command) {
                var handler = credits.getIfAvailable();
                if (handler == null) { throw unavailable(); }
                return handler.handle(command);
            }
            public MutationResult debit(DebitCommand command) {
                var handler = debits.getIfAvailable();
                if (handler == null) { throw unavailable(); }
                return handler.handle(command);
            }
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
