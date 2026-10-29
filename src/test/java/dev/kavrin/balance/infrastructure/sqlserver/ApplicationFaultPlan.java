package dev.kavrin.balance.infrastructure.sqlserver;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Arms one controlled HTTP operation's commit boundary and observes its reservation/recovery behavior.
 *
 * <p>Tests arm after fixture writes and issue one command at a time. The first commit is replay
 * lookup, the second is the writing transaction. Hidden reads simulate unavailable result visibility
 * after a real commit; they do not claim an actual database isolation or network failure.
 */
final class ApplicationFaultPlan {
    private volatile CommitFaultMode mode = CommitFaultMode.NONE;
    private final AtomicInteger untilFault = new AtomicInteger();
    private final AtomicInteger hiddenRemaining = new AtomicInteger();
    final AtomicInteger reservations = new AtomicInteger();
    final AtomicInteger hiddenReads = new AtomicInteger();
    final AtomicInteger droppedResponses = new AtomicInteger();
    volatile byte[] droppedBody;
    volatile int droppedStatus;

    void arm(CommitFaultMode selected) {
        mode = selected;
        untilFault.set(selected == CommitFaultMode.NONE ? 0 : 2);
        hiddenRemaining.set(0);
        reservations.set(0);
        hiddenReads.set(0);
        droppedResponses.set(0);
        droppedBody = null;
    }

    CommitFaultMode nextCommit() {
        return untilFault.get() > 0 && untilFault.decrementAndGet() == 0 ? mode : CommitFaultMode.NONE;
    }

    void hideCommittedReads() { hiddenRemaining.set(3); }

    boolean hideRead() {
        if (hiddenRemaining.get() <= 0) { return false; }
        hiddenRemaining.decrementAndGet();
        hiddenReads.incrementAndGet();
        return true;
    }
}
