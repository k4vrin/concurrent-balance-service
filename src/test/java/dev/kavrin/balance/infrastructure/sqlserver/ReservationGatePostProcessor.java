package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaCreditStore;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaDebitStore;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaTransferStore;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.config.BeanPostProcessor;

import static org.mockito.ArgumentMatchers.any;

/**
 * Installs test-only reservation barriers around the real JPA adapters in each application context.
 *
 * <p>Spies call each adapter's real reservation and observe any genuine named-key collision.
 * An optional fault plan observes reservations and masks selected recovery reads after real SQL.
 * Spring still supplies persistence exception translation and transaction ownership. Registering
 * this processor before bean creation leaves production sources and HTTP handlers unchanged.
 */
final class ReservationGatePostProcessor implements BeanPostProcessor {
    private final ReservationRaceGate gate;
    private final ApplicationFaultPlan faults;

    ReservationGatePostProcessor(ReservationRaceGate gate) { this(gate, null); }

    ReservationGatePostProcessor(ReservationRaceGate gate, ApplicationFaultPlan faults) { this.gate = gate; this.faults = faults; }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JpaCreditStore store) {
            var spy = Mockito.spy(store);
            Mockito.doAnswer(this::reserve).when(spy).reserve(any());
            if (faults != null) { Mockito.doAnswer(this::replay).when(spy).replay(any(), any()); }
            return spy;
        }
        if (bean instanceof JpaDebitStore store) {
            var spy = Mockito.spy(store);
            Mockito.doAnswer(this::reserve).when(spy).reserve(any());
            if (faults != null) { Mockito.doAnswer(this::replay).when(spy).replay(any(), any()); }
            return spy;
        }
        if (bean instanceof JpaTransferStore store) {
            var spy = Mockito.spy(store);
            Mockito.doAnswer(this::reserve).when(spy).reserve(any());
            if (faults != null) { Mockito.doAnswer(this::replay).when(spy).replay(any(), any()); }
            return spy;
        }
        return bean;
    }

    private Object replay(InvocationOnMock invocation) throws Throwable {
        var actual = invocation.callRealMethod();
        return faults.hideRead() ? java.util.Optional.empty() : actual;
    }

    private Object reserve(InvocationOnMock invocation) throws Throwable {
        gate.beforeReservation();
        if (faults != null) { faults.reservations.incrementAndGet(); }
        try { return invocation.callRealMethod(); }
        catch (DuplicateOperation duplicate) { gate.record(duplicate); throw duplicate; }
    }
}
