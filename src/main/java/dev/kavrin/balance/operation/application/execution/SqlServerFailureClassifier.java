package dev.kavrin.balance.operation.application.execution;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;

/**
 * Identifies SQL Server failures eligible for retry after a separately confirmed rollback.
 *
 * <p>Only vendor codes 1205 and 1222 authorize the transient classification. Cause and JDBC
 * next-exception chains are traversed with cycle protection. Connection SQLState class 08, other
 * nonzero vendor codes or suppressed cleanup evidence disqualify retry; broad Spring exception
 * categories alone are insufficient.
 *
 * <p>This classifier neither proves rollback nor recognizes request-identity collisions. The
 * transaction coordinator and persistence adapters retain those responsibilities.
 */
public final class SqlServerFailureClassifier {
    public boolean retryableRollback(Throwable failure) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        boolean transientCode = false;
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!visited.add(current)) { continue; }
            if (current instanceof SQLException sql) {
                if (sql.getSQLState() != null && sql.getSQLState().startsWith("08")) { return false; }
                int code = sql.getErrorCode();
                if (code == 1205 || code == 1222) { transientCode = true; }
                else if (code != 0) { return false; }
                if (sql.getNextException() != null) { pending.add(sql.getNextException()); }
            }
            if (current.getCause() != null) { pending.add(current.getCause()); }
            // A cleanup failure makes the original transient classification unsafe.
            if (current.getSuppressed().length != 0) { return false; }
        }
        return transientCode;
    }
}
