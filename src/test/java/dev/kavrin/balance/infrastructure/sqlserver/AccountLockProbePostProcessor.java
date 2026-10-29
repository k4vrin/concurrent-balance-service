package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.account.infrastructure.persistence.AccountEntity;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanPostProcessor;

import static org.mockito.ArgumentMatchers.anyCollection;

/**
 * Installs passive test observations around each application's real AccountStore lock call.
 *
 * <p>The real adapter performs every SQL query, refresh and ordering decision. Observations mark
 * entry and copy returned key order without imposing waits, replacing participant state or selecting outcomes.
 */
final class AccountLockProbePostProcessor implements BeanPostProcessor {
    private final AccountLockProbe probe;

    AccountLockProbePostProcessor(AccountLockProbe probe) { this.probe = probe; }

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof AccountStore store)) { return bean; }
        var spy = Mockito.spy(store);
        Mockito.doAnswer(call -> {
            Collection<AccountId> participants = call.getArgument(0);
            probe.before(List.copyOf(participants));
            var result = (Map<AccountId, AccountEntity>) call.callRealMethod();
            probe.after(List.copyOf(result.keySet()));
            return result;
        }).when(spy).lockAll(anyCollection());
        return spy;
    }
}
