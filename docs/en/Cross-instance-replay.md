# CBS17 cross-instance transaction-ID races

[فارسی](../fa/Cross-instance-replay.md) · [Distinct-operation concurrency](Concurrency-execution.md) · [Retry recovery](Retry-execution.md) · [Implementation status](Implementation-status.md)

## One identity arriving at two servers

Two application instances receive a debit with the same transaction ID at nearly the same time. Both initially find no completed operation. Only one reservation may win; the losing transaction must end before it reads the winner's committed result. Matching intent returns the original response. A different amount, participant or operation type must conflict without another financial effect.

`SqlServerCrossInstanceIdentityIT` now exercises this through two real HTTP application contexts against the existing dedicated SQL Server test database. Production handlers, adapters, financial rules, schema and configuration remain unchanged.

## Independent applications and coordinated reservation

`RunningLedgerApplication` starts two Spring contexts with separate ephemeral loopback HTTP ports. Setup asserts distinct credit/debit/transfer handler instances, transaction managers, entity-manager factories and datasource objects. Each application owns a separate two-connection Hikari pool. Both use the same explicit test database and funding policy. Fixture accounts are created with the restricted initializer identity and funded through the real credit handler with balanced history.

A test-only `ReservationGatePostProcessor` installs spies around the real credit/debit/transfer JPA stores before handlers are assembled. Each spy waits at `ReservationRaceGate` before invoking the actual reservation SQL. Reaching this point means that application's initial canonical replay lookup found no result. A four-second barrier releases both initial reservations; subsequent retries do not rejoin it. Client submission uses the existing bounded HTTP batch helper, and every worker outcome is collected.

The database chooses the winner. The losing adapter must throw `DuplicateOperation` containing actual SQL Server 2601/2627 evidence naming `uq_operations_transaction_id`. Every race requires exactly two reservation arrivals and one genuine named-key collision. No duplicate error or completed outcome is injected. After both requests finish, the gate is disarmed so cross-application replays run without coordination.

## Sixteen cases

| Race | Variants | Required result |
| --- | --- | --- |
| Identical successful intent | Credit, debit, transfer | Both return HTTP 200 and identical original bytes; one terminal operation, one balanced journal and two postings. |
| Identical business rejection | Over-cap credit; insufficient-funds debit and transfer | Both return the same original HTTP 409 response; one rejected operation and zero journals/postings. Replays after top-up remain identical. |
| Same ID, different amounts | Credit, debit, transfer | One success and one TRANSACTION_ID_CONFLICT; only the winning amount changes balances/history. |
| Same ID, different customer IDs | Credit target; debit or transfer source | One success and one conflict; the losing participant's balance is unchanged. |
| Same ID, different operation types | Credit/debit, credit/transfer, debit/transfer | The global identity namespace retains only one operation type and one financial effect. |
| Same ID, different transfer recipients | One source, two recipients | Only the winning recipient receives funds; the other remains unchanged. |

Tests accept either initial request as winner. Independent SQL requires exactly one retained operation with the expected type, amount, terminal state, HTTP status and response bytes. Success requires one journal, two postings and exact per-operation debit/credit equality using `decimal(38,0)`. Rejection requires no financial history. No committed PENDING result is accepted.

Expected customer, recipient and funding balances are checked independently, then reconstructed from immutable postings in a quiescent single-query view. Each competing intent is replayed through both applications: matching requests retain their response, and losing requests retain their conflict. History counts and all relevant projections are checked again after these replays. Rejected debit/transfer commands replay after replenishment without reevaluating available funds; over-cap credit rejection is also retained after a top-up.

## Verification, 2026-10-29

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 178 real SQL Server tests**, with **zero failures/errors/skips**, in **55.043 seconds**. Fresh Surefire/Failsafe XML totals were checked.

The focused first run passed all 16 new integration cases alongside 421 portable tests in 20.442 seconds. Every race observed a real named SQL Server uniqueness collision. All five new Java types pass Javadoc/doclint. The compiler syntax-tree scan checked all 152 Java files and found zero nested/local/anonymous types. Changed-document links, whitespace and exact credential scans of logs/reports passed.

## Evidence boundary and remaining gates

The applications have independently created Spring and persistence resources but share one JVM and host. The synchronization gate is shared test instrumentation, not a production coordination mechanism. These cases establish selected cross-context HTTP/database races; they do not prove separate-process crash isolation, every interleaving, actual network partitions or throughput.

Opposite-direction transfers, independent-account progress while another account is blocked and broader response/commit fault recovery remain CBS18–19. Authentication/ownership, independent learner acceptance and production readiness remain separate. The existing SQL Server ARM64 emulation is unsupported by Microsoft.

CBS16 was fast-forwarded into main and pushed as `773c451`. This checkpoint includes the cross-instance race suite on `test/cross-instance-idempotency-races`, together with the complete type-level Javadoc audit.
