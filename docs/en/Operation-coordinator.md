# Operation coordinator and SQL Server locking

Production components no longer carry smoke-profile exclusions. The standalone HTTP assembly is defined only in test sources. See the [class walkthrough](Credit-class-walkthrough.md) for wiring, class responsibilities and request flows. Historical checkpoint notes below describe the earlier wiring.

[فارسی](../fa/Operation-coordinator.md) · [Persistence](Persistence.md) · [Accepted contract](CBS09-contract.md) · [ADR-010](../adr/ADR-010-persistence-contract.en.md)

This guide explains the coordinator foundation added on 2026-10-24, its design alternatives and the account-lock protocol it will surround. It distinguishes current code, planned financial execution and existing test evidence. `READ_COMMITTED` is a database isolation level; rate limiting is a separate request-admission concern.

## Start with the failure we must prevent

A credit changes several records. Its operation identity, balanced journal, two postings, both balance projections and retained success response must commit together. A balance without matching history cannot be reconciled; history without its matching projection or outcome is also incomplete.

The planned credit attempt is:

```text
Validate request shape outside the transaction
Coordinator starts its own transaction
    Reserve operation identity before account locks
    Resolve and lock the complete participant set in the shared order
    Validate locked state, policy and all proposed balances
    Persist journal, postings, projections and terminal response
Commit
Return the retained result
```

The coordinator owns the transaction boundary. The credit handler supplies the business work; persistence adapters perform database access. No network delivery, cache write or message publication belongs inside this locked attempt.

If a technical error occurs after projection updates but before outcome persistence and rollback is confirmed, this attempt leaves zero new operations, journals or postings and both balances unchanged. Previously committed account records remain. A transient rolled-back failure may permit a bounded whole-attempt retry with the same transaction ID. A persistent defect requires repair. A connection error during commit requires same-ID recovery because the outcome may be uncertain. An error alone never proves rollback.

## Read the current implementation

Source: [OperationCoordinator.java](../../src/main/java/dev/kavrin/balance/operation/application/OperationCoordinator.java).

```java
@Service
public final class OperationCoordinator {
    private final TransactionTemplate transaction;

    public OperationCoordinator(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(5);
    }

    public <T> T execute(Supplier<T> attempt) {
        // ADR-010: joining a caller's transaction could return success before that caller commits.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation coordinator must own its transaction");
        }
        return transaction.execute(status -> attempt.get());
    }
}
```

`Supplier<T>` is a block of work returning a value of type `T`. The coordinator does not need to understand credit/debit/transfer outcomes to execute that block. A handler can return a retained business rejection normally so that its operation record commits. An unexpected runtime exception escapes and triggers rollback.

The callback result is only a candidate until transaction completion:

```text
begin → callback returns candidate → commit succeeds → execute returns candidate
begin → callback throws           → rollback        → exception escapes
begin → callback returns candidate → commit throws  → exception escapes
```

The last case does not imply the database rolled back. Commit failure and uncertain completion need recovery outside this minimal class. See [Spring's programmatic transaction documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html).

## The manager is a production dependency

`TransactionTemplate` provides callback execution and transaction settings. `PlatformTransactionManager` performs transaction management against the configured persistence infrastructure. For this project's JPA path, the intended runtime manager is the same `JpaTransactionManager` used by the participating persistence code. Wiring that path remains part of financial integration work.

Both construction styles below need a manager:

```java
var direct = new TransactionTemplate(manager);

var configuredLater = new TransactionTemplate();
configuredLater.setTransactionManager(manager);
```

The no-argument constructor permits later configuration; it does not create a database transaction manager. Another valid design injects an already configured template into the coordinator. Our constructor instead accepts the manager and creates a private template so its settings belong to this coordinator rather than a shared mutable template bean. Constructor injection also makes the dependency visible and permits recording test infrastructure. Testing is a benefit, not the reason the runtime needs a manager. [Spring's TransactionTemplate API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionTemplate.html) documents the constructor and manager requirement.

## Why reject an existing transaction?

The template defaults to REQUIRED propagation. Without the guard it could join a caller's transaction:

```text
caller begins transaction
    coordinator joins it
    callback completes
    coordinator returns a candidate success
caller later rolls back
```

That violates our coordinator's return-after-own-commit contract. Rejecting an ambient transaction makes the calling rule explicit. Both writable and read-only ambient transactions are rejected.

REQUIRES_NEW would instead create an independent transaction while suspending the outer one. An inner credit could then commit even if the outer work rolls back. That is a different composition contract, so we deliberately reject the outer transaction rather than silently introduce this behavior. [Spring's propagation reference](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html) explains these choices.

`@Transactional` remains a valid alternative. We chose a template because the coordinator explicitly surrounds one attempt, and future exception classification, identity recovery and backoff need a visible boundary after transaction cleanup. We have not yet implemented that retry loop. The choice is about this control flow, not a general weakness in annotations.

## Why READ_COMMITTED is not enough by itself

Our selected local SQL Server configuration has READ_COMMITTED_SNAPSHOT disabled. Ordinary READ_COMMITTED reads use locking to avoid dirty reads, and do not retain read protection for every value until transaction completion. With RCSI enabled, ordinary reads would instead use statement-level row versions; do not generalize this project's blocking observations to that configuration.

Preventing dirty reads does not protect this application sequence:

```text
A reads committed balance 100
B reads committed balance 100
A decides debit 80 is affordable
B decides debit 80 is affordable
```

Both decisions used committed data. The financial decision needs protection through its update and commit. Our protocol acquires that protection before reading the decision-making account state. [Microsoft's locking guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide?view=sql-server-ver16) explains isolation, lock modes and row versioning.

## UPDLOCK and HOLDLOCK do different jobs

The adapter uses this parameterized lookup inside the owning writable transaction:

```sql
SELECT account_id, category, currency, balance, status
FROM dbo.accounts WITH (UPDLOCK, HOLDLOCK)
WHERE account_id = CAST(? AS uniqueidentifier);
```

`UPDLOCK` requests update locks for the read and retains them until transaction completion. It announces an intention to update without performing the update. `HOLDLOCK` supplies SERIALIZABLE semantics for the referenced table access, including predicate protection where needed. It is more than a request to prolong a row lock: it protects a missing-key lookup against a matching insertion too. [Microsoft's table-hint reference](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table) defines both hints.

For the same lock resource, the simplified S/U/X compatibility matrix is:

| Held / requested | Shared S | Update U | Exclusive X |
| --- | --- | --- | --- |
| Shared S | Compatible | Compatible | Wait |
| Update U | Compatible | Wait | Wait |
| Exclusive X | Wait | Wait | Wait |

This omits intent/range modes and granularity. An update lock is not an exclusive lock: an ordinary compatible reader can still read before the writer converts to X. Once the write needs X, existing readers can delay it. Query plans, index keys, pages and escalation determine actual resources; the hints do not promise row-only locking or universal independent progress.

### UPDLOCK example: decide who may update first

With a plain SERIALIZABLE SELECT, two transactions can both obtain shared protection on an existing account. If both then try to update it, each can wait for the other's shared lock to disappear. SQL Server may select a deadlock victim.

With our lookup, the second updater waits at the read:

```text
A obtains U on customer C and reads 100
B requests U on C and waits
A validates debit 80, writes 20, commits
B obtains U and reads 20
B can now reject debit 80 using current protected state
```

The example describes a future debit flow, not an implemented handler. It avoids this shared-to-exclusive conversion pattern, not every possible deadlock. Ordered multi-account locking and whole-attempt recovery remain necessary.

### HOLDLOCK example: protect absence

Suppose account M does not exist. The lookup returns no row. There is no existing row on which an update lock alone can express the complete absence guarantee.

```text
A looks up missing M with UPDLOCK, HOLDLOCK
A retains protection for the searched key range
B tries to insert M and waits
A commits its missing-account rejection, or rolls back
B's insertion can proceed after protection is released
```

The protected gap may include other keys, so an insert of a different missing UUID can also wait. Exact coverage depends on the index and plan. With an existing unique-key match, range protection need not be identical to a missing-key search. Closing the result set does not release these transaction-duration locks. In autocommit, however, the statement's transaction ends with the statement; the protocol requires an explicit transaction spanning the decision and writes.

## Why not SERIALIZABLE for the entire transaction?

Our accepted choice is READ_COMMITTED plus stronger protection on financial account lookups. SERIALIZABLE broadens protection to other ordinary reads. It does not run all transactions globally one at a time. If the workload consists only of our protected account lookups and writes, switching the baseline may add little cost or protection. We have no measured evidence that READ_COMMITTED makes the current credit path faster.

### Extra point read: longer blocking

Hypothetically, A credits customer A but also reads customer B for information using a plain indexed SELECT. It never changes B. A separate transaction wants to update B.

| A's isolation | Consequence of A's ordinary read of B |
| --- | --- |
| Locking READ_COMMITTED | Read protection is released as the read completes; that completed read does not keep the updater waiting until A commits. |
| SERIALIZABLE | Read protection remains until A ends, delaying the incompatible update. |

If A reads B at 2 ms and commits at 50 ms, the updater could wait for much of the remaining interval. These times are illustrative. Waiting increases latency, occupies connections and can reduce throughput under contention; it does not mean each SQL statement needs more CPU. Our current credit design does not require this extra B read.

### Range read: blocked insertion

Consider this hypothetical additional query:

```sql
SELECT transaction_id
FROM dbo.operations
WHERE transaction_id >= N'credit-100'
  AND transaction_id <  N'credit-200';
```

Under SERIALIZABLE, an indexed range plan can prevent inserting `credit-150` until completion, preserving the result set. A completed ordinary READ_COMMITTED query does not preserve that range. This is useful when the business decision requires an unchanged set. It can add unnecessary waiting for an informational read. Poor access paths can broaden locking; indexing and plans matter.

### Shared funding remains a bottleneck

Two credits to different customers can still update the same funding account. They must coordinate on that account under either design. Local HOLDLOCK also retains range protection, so our design does not eliminate blocking. We would compare actual plans, lock waits, deadlocks, latency and throughput on the same workload before changing or making performance claims about the baseline.

## Complete protocol and limits of the current class

| Mechanism | Responsibility |
| --- | --- |
| Coordinator-owned transaction | Commit or roll back the whole attempt; return after successful completion |
| READ_COMMITTED | Baseline isolation for ordinary reads |
| UPDLOCK / HOLDLOCK | Protect decision-making account state and relevant absence |
| Complete participant set, deduplicated descending application UUID order | Reduce opposite-order multi-account deadlocks; do not substitute SQL GUID order |
| Global transaction-ID uniqueness and canonical comparison | Support durable replay/conflict handling |
| Balanced immutable history and atomic projections/outcome | Preserve the accepted ledger contract |
| Recovery and reconciliation | Resolve retries/uncertain completion and independently check committed effects |

The coordinator remains a small transaction wrapper registered with `@Service`. The [durable credit handler](Credit-execution.md) now uses it for replay lookup and reservation/history/projection/outcome persistence. It sets READ_COMMITTED and a 5-second transaction timeout. General retry budgets, backoff and uncertain-commit recovery remain pending; debit and transfer are still unavailable. Historical evidence below describes each earlier increment.

## Evidence and review route

The five [OperationCoordinatorTests](../../src/test/java/dev/kavrin/balance/operation/application/OperationCoordinatorTests.java) use a recording transaction manager: they check writable READ_COMMITTED execution, return after commit, rollback on callback exception, commit-failure propagation and rejection of both ambient transaction kinds. Java 21 `./mvnw -o -B -ntp verify` passed 245 portable tests with zero failures/errors/skips for this foundation. They do not prove database commit, stored replay or SQL Server rollback.

Earlier [persistence tests](Persistence.md) exercised actual JPA account lock lifetime, freshness, committed HTTP reads and opening rollback boundaries; the documented full run passed 103 SQL Server tests alongside 240 portable tests. That historical run predates the coordinator and was not rerun for its foundation. The SQL Server ARM64 environment remains an unsupported local experiment. The timelines in this guide are teaching examples, not executed benchmarks.

Review the constructor first, then the ambient guard, then the candidate-result/commit boundary. Next compare the two hints and the missing-key example with AccountStore. The durable credit slice must finally verify this coordinator with the real JPA manager and all financial writes in the same physical transaction.

## Spring wiring checkpoint, 2026-10-24

`OperationCoordinator` is now a `@Service` with `@Profile("!smoke")`, matching the persistence components. Spring supplies its constructor's transaction manager in the normal application context; the test-only smoke context excludes the coordinator because it intentionally has no persistence infrastructure. Credit-handler wiring is still pending. Two new portable context tests verify manager injection and smoke exclusion. Full Java 21 `./mvnw -o -B -ntp verify` passed 247 tests, zero failures/errors/skips, in 8.219 seconds.

Three new `SqlServerPersistenceIT` cases are written for JPA participation/READ_COMMITTED and committed probe visibility, rollback visible from a fresh JDBC connection, and ambient JPA transaction rejection. They use an existing uniquely named temporary-table fixture and do not write financial history. They remain unverified: the initial database attempt failed at setup with connection refused; restarting the existing container failed with SQL Server page-allocation errors 17300/17312. The full profile attempt also failed at database setup with 17 errors, not passing database tests. The container reported exit 235; no volume was deleted, schema changed or Docker resource setting changed. Historical 103-test database evidence above does not verify this increment. Restore the local SQL Server environment and rerun `./mvnw -Psqlserver-it verify` before claiming runtime JPA acceptance.

## Successful database retest, 2026-10-24

The coordinator/JPA checks above are now verified. SQL Server was healthy internally, but Docker's active published-port map was empty despite the retained 127.0.0.1:14333 configuration. Restarting the existing container restored that mapping without changing the image, volume, schema or resource settings. This connectivity fault is distinct from the earlier startup memory failure; its resolution does not establish that failure's cause.

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed 247 portable plus 106 SQL Server tests, zero failures/errors/skips, in 27.706 seconds. Fresh XML totals were checked. All three new coordinator cases passed: the injected manager is JpaTransactionManager, the EntityManager joins a READ_COMMITTED transaction, a committed probe is visible from another connection, a failed probe rolls back, and an ambient JPA transaction is rejected before work. Temporary probes clean up their own generated tables; normal opening tests retain their synthetic committed ledger history. No configured passwords appeared in the final log/reports. This supersedes the unverified disposition above, not the recorded failure history. Credit execution/replay/recovery and formal learner acceptance remain pending; ARM64 emulation remains unsupported.
