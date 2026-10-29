package dev.kavrin.balance.infrastructure.sqlserver;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

/** Installs the test transaction wrapper before application handlers receive their manager. */
final class CommitFaultPostProcessor implements BeanPostProcessor {
    private final ApplicationFaultPlan plan;
    CommitFaultPostProcessor(ApplicationFaultPlan plan) { this.plan = plan; }
    @Override
    public Object postProcessAfterInitialization(Object bean, String name) {
        return bean instanceof PlatformTransactionManager manager ? new FaultTransactionManager(manager, plan) : bean;
    }
}
