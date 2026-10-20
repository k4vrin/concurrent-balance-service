# SQL Server query-plan and throughput review

Status: procedure only; no SQL Server query or financial workload has been measured. [فارسی](../fa/Query-performance.md)

## Query inventory

| ID / phase | Query | Candidate access path | Separate concurrency concern |
| --- | --- | --- | --- |
| Q01 / P1 | Account lookup and lock | Account primary key | Hot account lock wait; verify actual Hibernate SQL and lock behavior |
| Q02 / P1 | Operation by transaction ID | Unique transaction-ID index | Actor access before replay; collation and trailing-space identity semantics |
| Q03 / P4 | Intent by ID/owner/state | Intent primary key | Intent first, accounts in fixed order; failed attempts persist |
| Q04 / P6 | Bounded eligible outbox claims | Evaluate status/next-attempt/ID | Worker ownership, lease expiry and crash recovery |
| Q05 / P6 | Account history page | Evaluate account/time/ID | Keyset pagination and a stable tie-breaker |
| Q06 / P6 | Consumer dedup insert | Unique consumer/event identity | Dedup and effect in one transaction |

These are hypotheses. Inspect generated SQL, bind data types, implicit conversions, actual isolation and database settings such as READ_COMMITTED_SNAPSHOT before choosing hints/indexes. SQL Server may lock keys, pages or tables and can escalate locks; ordered account IDs alone do not justify claiming every possible deadlock is eliminated. Deadlock victims need whole-transaction retry with a bounded policy and unchanged operation identity.

## Collect an actual plan

1. Record commit, schema, server version/edition, database compatibility level, row counts/skew, statistics freshness, pool size and query binds using synthetic values.
2. Seed representative data outside timed runs and collect relevant statistics. Preserve the same dataset/settings for before/after comparisons.
3. In SSMS enable Include Actual Execution Plan, then run the real query with representative parameters and actual application fetch behavior. Estimated plans are planning evidence, not executed row counts.
4. Enable `SET STATISTICS IO ON` and `SET STATISTICS TIME ON` in that diagnostic session. Save the actual `.sqlplan` and Messages output. Use parameterized execution matching the app, not only literal SQL with a different plan. Never clear shared production caches to improve a demonstration.
5. Inspect estimated/actual rows, seeks/scans, key lookups, logical reads, spills, sort costs, implicit conversions, memory grants, CPU/elapsed time and parameter-sensitive behavior. A scan is not automatically bad and optimizer cost is not throughput.
6. Separate execution cost from blocking, deadlocks, connection-pool waits and application queueing. Use approved diagnostic access and Extended Events/deadlock evidence as needed; an excellent lookup plan can still wait on a hot account.
7. Change one hypothesis, repeat the plan review and then run equivalent application load and correctness checks. Do not leave diagnostic instrumentation enabled in baseline load measurements without accounting for its cost.

Example only, after a matching schema exists. Match `@tx_id` to the real column type/length/collation; do not copy a string parameter onto a differently typed indexed column:

```sql
SET STATISTICS IO ON;
SET STATISTICS TIME ON;

EXEC sys.sp_executesql
    N'SELECT transaction_id, request_hash, outcome
      FROM dbo.operations WHERE transaction_id = @tx_id;',
    N'@tx_id varchar(64)',
    @tx_id = 'synthetic-operation-001';

SET STATISTICS TIME OFF;
SET STATISTICS IO OFF;
```

No operations table exists yet. Locking queries and outbox claims need separate executed-plan and concurrency tests once implemented. Explicitly commit/rollback diagnostic transactions holding locks. Do not grant broad server rights to the application for plan collection. Plans can include sensitive values; share sanitized synthetic evidence.

## Workload evidence

Measure independent accounts, hot accounts, duplicates, mixed reads/mutations and external-service outages separately. Fix hardware, versions, durability, data size/skew, pool, offered load and workload mix; warm up, repeat and report spread. Report committed successful operations/second, offered load, rejection/error rates, p50/p95/p99, logical reads, transaction-log writes, lock waits, pool waits, CPU and GC. Faster rejection is not higher financial throughput. No numeric SLO or constraint-overhead percentage is claimed before measurement.

Record each run under `docs/performance/<query-id>/<run-id>/`: commit/date, schema/server/compatibility, sanitized SQL and bind types, dataset, plan and statistics, baseline/change, raw samples, latency/throughput spread, correctness checks, remaining bottleneck and keep/revert decision. Large raw outputs may remain ignored if collection is reproducible. Query Store can help track plan/runtime history later; check its availability/configuration for the selected server.

Sources: [actual plans](https://learn.microsoft.com/en-us/sql/relational-databases/performance/display-an-actual-execution-plan), [STATISTICS IO](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-io-transact-sql), [STATISTICS TIME](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-time-transact-sql), [locking](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide). Verify against the pinned server version.
