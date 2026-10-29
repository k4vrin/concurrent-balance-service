# CBS18 opposite transfers and independent progress

[فارسی](../fa/Transfer-progress.md) · [Cross-instance identity](Cross-instance-replay.md) · [Distinct commands](Concurrency-execution.md) · [Implementation status](Implementation-status.md)

## Why unrelated work must keep moving

A transfer is waiting for an externally locked source account. A request using other existing accounts should still finish, including through the same application. Global request serialization or an unintended shared participant would couple unrelated work to that stalled transfer.

Opposite-direction transfers create another challenge: both requests must resolve their full participant set using the same application UUID order. Their source/destination directions must not become different account-lock sequences.

`SqlServerTransferProgressIT` adds ten real HTTP-to-SQL Server cases. Production code, schema and runtime configuration remain unchanged.

## Two opposite transfers

Two independently assembled applications receive A-to-B and B-to-A commands with distinct transaction IDs. Both accounts initially hold 100 rials. The reservation barrier releases both attempts together, while actual SQL locking remains in `AccountStore`.

Two parameter sets cover amounts 30/20 and exact available amounts 100/100. Both commands must succeed, each retaining one operation, one balanced journal and two postings. Expected final balances are 90/110 for the first pair and 100/100 for the second; funding remains unchanged.

A passive `AccountLockProbe` copies the key order returned after each real account-lock call. Both results must equal the shared descending unsigned UUID order, regardless of direction. This observes the adapter's reported acquired-participant order, not an independent driver-level statement/lock trace. Replay through both applications must preserve original response bytes, counts and independently reconstructed balances.

## Eight independent-progress cases

Each case starts a blocked A-to-D transfer of 40 rials from a 100-rial source. A separate restricted-runtime JDBC connection holds an actual account update lock using a no-op projection update. The test observes entry into the real lock adapter and the writer's uncommitted PENDING reservation, then issues unrelated work while keeping that external transaction open.

| Unrelated request | Initial unrelated balances | Required effect before blocker release |
| --- | --- | --- |
| Credit 25 | Customer 100 | Customer 125, funding +25, one completed journal/two postings. |
| Debit 25 | Customer 100 | Customer 75, funding -25, one completed journal/two postings. |
| Transfer 25 | Source 100, recipient 0 | Source 75, recipient 25, funding unchanged, one completed journal/two postings. |
| Balance GET | Customer 100 | HTTP 200 with the authoritative 100-rial balance; projections/history still reconcile. |

Each request runs once through the blocked writer's application and once through the second application, giving eight cases. Its response and independent SQL history/projection checks must finish while the external lock is still held and the blocked transfer's future is unfinished. The test then releases the lock, requires the blocked transfer to succeed exactly once, and checks source 60, recipient 40 and unchanged independent effects. Cross-application replay must return the transfer's retained bytes without another journal.

The PENDING observation uses READUNCOMMITTED only as test instrumentation to inspect the reserved row while its transaction is open. Production financial reads remain unchanged. Reconciliation runs only for unrelated accounts while the blocker is held and for all participants after it is released. It does not claim a globally consistent snapshot during arbitrary concurrent posting activity.

## Verification, 2026-10-29

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 188 real SQL Server tests**, with **zero failures/errors/skips**. Maven reported total time **01:00 min**. Fresh Surefire/Failsafe XML totals were checked.

The focused first run passed all ten new SQL Server cases alongside 421 portable tests in 21.133 seconds. Compiler DocTrees confirms class-level Javadoc for all 156 Java types, including the standalone probe, and whole-project private Javadoc/doclint passed without warnings/errors. The syntax-tree scan found zero nested/local/anonymous types in all 155 src Java files. Changed-document links, whitespace and exact credential scans of logs/reports passed.

The shared application fixture now accepts an optional passive account-lock probe. Its existing three-argument startup path remains intact, and the full CBS17 suite is included in regression. All observations call the real adapter and do not inject snapshots, business outcomes or database failures.

## Limits and next work

The applications own separate servers and persistence resources within one JVM. These are selected interleavings, not proof that all deadlocks are impossible, a throughput benchmark or a latency SLO. The unfinished-future checks establish ordering relative to blocker release rather than an exact service-time bound. The local SQL Server ARM64 emulation remains unsupported by Microsoft.

Broader commit/response fault recovery remains CBS19. Independent learner acceptance and the CBS20 core gate remain separate. CBS17 plus the complete 153-type Javadoc audit was committed and pushed as `9ea7d1f` on `test/cross-instance-idempotency-races`; this checkpoint includes CBS18 on the dependent `test/transfer-locking-and-independent-progress` branch.
