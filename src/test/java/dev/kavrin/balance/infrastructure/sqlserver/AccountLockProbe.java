package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Observes entry to real account locking and the ordered identifiers returned after acquisition.
 *
 * <p>The probe never substitutes snapshots or acquires/releases financial locks. A selected
 * blocked participant triggers a bounded test latch; completed calls retain their adapter-returned
 * order for assertions. Tests reset observations only when fixture/request workers are quiescent.
 */
final class AccountLockProbe {
    private final ConcurrentLinkedQueue<List<AccountId>> completed = new ConcurrentLinkedQueue<>();
    private volatile AccountId blocked;
    private volatile CountDownLatch entered = new CountDownLatch(0);

    void reset() { blocked = null; entered = new CountDownLatch(0); completed.clear(); }
    void observeBlocked(AccountId id) { blocked = id; entered = new CountDownLatch(1); }
    boolean awaitBlockedEntry() throws InterruptedException { return entered.await(4, TimeUnit.SECONDS); }

    void before(List<AccountId> participants) {
        if (blocked != null && participants.contains(blocked)) { entered.countDown(); }
    }

    void after(List<AccountId> acquired) { completed.add(List.copyOf(acquired)); }
    List<List<AccountId>> completedOrders() { return List.copyOf(completed); }
}
