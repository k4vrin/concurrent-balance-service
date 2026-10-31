# SQL Server locking and access-path experiments

[فارسی](../fa/SQL-server-knobs.md) · [Test setup](SQL-server-tests.md) · [Documentation index](../README.md)

A locked account must remain part of a transfer. Silently skipping it would break the command's meaning. A work queue has a different requirement: a worker may claim another available item while its peer owns the first one. SQL Server exposes controls for both cases, but their correctness depends on the query's purpose.

[SqlServerKnobsIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerKnobsIT.java) explores the index and query controls in disposable synthetic fixtures. It uses the existing restricted test identity and dedicated `cbs_test` connection guard. Generated global temporary tables live in `tempdb` and are explicitly dropped after each case. The experiments require no business migration or new grants.

## What the seven cases check

| Cases | Experiment | Assertion |
| --- | --- | --- |
| 3 | `ALTER INDEX ALL ... SET (ALLOW_ROW_LOCKS = ..., ALLOW_PAGE_LOCKS = ...)` | Row-allowed/page-disabled, row-disabled/page-allowed and both-disabled settings appear in `tempdb.sys.indexes`; the fixture remains readable. |
| 2 | `WITH (INDEX(1), FORCESEEK)` and `WITH (INDEX(1), FORCESCAN)` | Both statements execute and return the same selected fixture value. Index 1 is this fixture's clustered primary key. |
| 1 | One session retains `XLOCK, HOLDLOCK, ROWLOCK` on key 1 | A normal lock-based reader gets error 1222. `READPAST` returns only key 2 while the lock remains held, then keys 1 and 2 after rollback. |
| 1 | One session retains `XLOCK, HOLDLOCK, PAGLOCK` | Even with `READPAST`, the reader gets error 1222 until the holder rolls back. |

The holder acquires its lock synchronously before the reader starts, and retains it after closing the result set. No sleeps or scheduling assumptions establish contention. Each reader has `LOCK_TIMEOUT 250`; the JDBC helper also bounds network waits. Both sessions use `READ_COMMITTED`; `READCOMMITTEDLOCK` explicitly selects lock-based reading even if the database enables RCSI. Each fixture uses only two rows, so this does not exercise lock escalation under load.

## Interpret the comment precisely

`ALLOW_ROW_LOCKS` and `ALLOW_PAGE_LOCKS` control which granularities SQL Server may use for an index. Enabling a type does not force every query to use it. When both are disabled, table locking remains possible. The metadata cases check configuration; the cross-session cases separately check observable blocking/skipping with explicit granularity hints. They do not enumerate every internal lock or establish that escalation cannot happen. See Microsoft's [index options](https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-table-index-option-transact-sql?view=sql-server-ver16) and [index locking options](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-indexoption-transact-sql?view=sql-server-ver17).

`FORCESEEK` and `FORCESCAN` restrict the optimizer's access-path choices. These are stronger than a preference and can exclude a useful plan or make a query impossible to plan. The two cases verify accepted hints and returned data. They do not capture execution-plan XML, compare logical reads, or establish which path is faster. A useful follow-up needs representative cardinalities, actual plans and `STATISTICS IO/TIME`, with any required SHOWPLAN permission reviewed separately. See Microsoft's [table hints reference](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table?view=sql-server-ver17).

`READPAST` skips row locks, not page locks. It does not read the uncommitted value and does not promise a complete result set or freedom from all blocking. The omitted row returns after the holder releases its lock. SQL Server documents this mainly for work queues, with isolation-level and RCSI restrictions. The tests use `READCOMMITTEDLOCK` deliberately. See the same [table hints reference](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table?view=sql-server-ver17).

The claim that almost any edge case can be solved by configuration is an opinion these tests cannot establish. More controls create more combinations to review and test.

## Run the lab

Start the existing provisioned SQL Server environment described in [Docker.md](Docker.md). Export the dedicated `SQLSERVER_TEST_URL`, `SQLSERVER_TEST_USERNAME` and `SQLSERVER_TEST_PASSWORD` through the existing [test setup](SQL-server-tests.md). With Java 21:

```bash
./mvnw -Psqlserver-it -Dit.test=SqlServerKnobsIT verify
```

This still runs the portable suite first. Missing infrastructure fails class setup rather than skipping cases. Inspect the fresh `target/failsafe-reports/TEST-dev.kavrin.balance.infrastructure.sqlserver.SqlServerKnobsIT.xml`; require seven tests and zero failures, errors and skips. Run `./mvnw -Psqlserver-it verify` for the full regression. Normal `./mvnw verify` compiles this class but does not execute SQL Server integration tests.

## Application boundary

These experiments do not change account locking, transaction-ID recovery, ledger writes, balances or migrations. `READPAST` has no place in the current account lookup, funds check, replay lookup or reconciliation: skipping a required account, retained outcome or posting changes the answer. A future outbox worker could evaluate queue-style claiming separately, with its own atomic claim/retry contract. No outbox is implemented here.

No benchmark result or core-acceptance count follows from these synthetic engine probes. The local SQL Server ARM64 emulation remains the unsupported experiment documented in the environment guide.

## Recorded verification, 2026-10-30

Java 21.0.11 with SQL Server 16.00.4225 and Microsoft JDBC 13.4.0.0 passed the focused command with **421 portable + 7 SQL Server tests**, zero failures/errors/skips, in 9.464 seconds. Full `./mvnw -Psqlserver-it verify` then passed **421 portable + 219 SQL Server tests**, zero failures/errors/skips, in 01:09 min. Fresh Surefire/Failsafe XML totals were checked; the seven lab cases took 0.924 seconds in the full run. These additional engine probes do not expand the financial acceptance scope.

The initial local attempts failed before the experiments could run: test settings were not yet exported, Docker needed its privileged helper repaired, and the existing 4 GB SQL Server container subsequently exited during startup with error 701 for the internal memory pool. The passing runs used a temporary 6 GB container limit while SQL Server's configured memory setting remained 2,048 MB. After verification, the container was stopped and its 4 GB limit restored. Repository Compose settings and database volumes were not changed. This observation is a local startup workaround, not a measured capacity recommendation.
