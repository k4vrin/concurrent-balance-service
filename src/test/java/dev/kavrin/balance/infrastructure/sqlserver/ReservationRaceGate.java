package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import java.sql.SQLException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coordinates two applications after replay found no outcome and before either reservation executes.
 *
 * <p>Only the first two reservation calls join the bounded barrier; later retries remain free to
 * recover. Observed duplicates must contain actual SQL Server evidence for the named global
 * transaction-ID constraint. No query, lock, failure or committed outcome is fabricated.
 * Each test arms one race and disarms it only after both HTTP workers finish.
 */
final class ReservationRaceGate {
    private volatile CyclicBarrier barrier;
    private final AtomicInteger arrivals = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();

    void arm() {
        arrivals.set(0);
        duplicates.set(0);
        barrier = new CyclicBarrier(2);
    }

    void disarm() { barrier = null; }
    int arrivals() { return arrivals.get(); }
    int duplicates() { return duplicates.get(); }

    void beforeReservation() throws Exception {
        var current = barrier;
        if (current != null && arrivals.incrementAndGet() <= 2) { current.await(4, TimeUnit.SECONDS); }
    }

    void record(DuplicateOperation duplicate) {
        boolean namedSqlCollision = false;
        for (Throwable cause = duplicate; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)
                    && sql.getMessage().contains("uq_operations_transaction_id")) {
                namedSqlCollision = true;
            }
        }
        assertThat(namedSqlCollision).as("actual named SQL Server identity collision").isTrue();
        duplicates.incrementAndGet();
    }
}
