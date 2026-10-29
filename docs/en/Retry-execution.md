# CBS15 transaction retry and uncertain-outcome recovery

[فارسی](../fa/Retry-execution.md) · [Accepted contract](CBS09-contract.md) · [Coordinator](Operation-coordinator.md) · [Implementation status](Implementation-status.md)

## The failure we must recover

A transfer of 40 rials from a 100-rial account may reserve its identity and then hit a locked account. SQL Server error 1222 can leave the transaction active. Retrying only the locking statement would keep earlier transaction state alive. The service must finish rollback, recover the same identity, and only then consider a complete new attempt.

A failed commit acknowledgement has a different meaning. The database may already have committed the transfer. Returning the callback's candidate result or immediately executing it again is unsafe. The service must recover the original retained response, or return HTTP 503 while the caller keeps the same transaction ID.

## Current flow

Credit, debit and transfer delegate to `OperationCoordinator.executeRecovering`. `execute` still means one transaction and remains the primitive for balance lookup and each recovery read. The coordinator rejects ambient transactions before any lookup or preparation.

1. Check for a committed outcome using full canonical intent and validate its retained response. This happens before mutable currency, cap or funding policy evaluation.
2. Prepare the operation ID, journal ID, command, policy snapshot and any funding selection once. Every writing retry receives that same attempt object.
3. Execute the complete reserve/lock/check/history/projection/outcome callback inside an owned transaction. `AttemptCompletion` records Spring's `afterCompletion` status.
4. After reported rollback, classify the failure outside the transaction. A transient vendor code permits fresh identity recovery and bounded whole-attempt retry. A business rejection is a normal committed value and is never retried.
5. If completion is unknown or a commit acknowledgement failed, perform bounded recovery reads only. A matching retained outcome returns its original response; a conflicting intent fails. Empty recovery reads never authorize another write in this request. If still unresolved, return 503 with the original failure as cause.

Each fresh attempt gets a new transaction and persistence context. Classification, recovery decisions and jitter happen after the previous transaction has completed cleanup. The deadline never interrupts cleanup or converts uncertain completion into a rollback claim.

## Failure decisions

| Evidence | Action |
| --- | --- |
| Reported rollback and SQL Server 1205 or 1222 | Fresh same-ID lookup, then retry the complete prepared attempt if absent and budget permits. |
| Named transaction-ID collision translated to `DuplicateOperation` | Recover after the failed transaction; return retained outcome/conflict. An empty lookup returns unavailability. |
| Commit failure or unknown cleanup/completion | Bounded same-ID recovery only; no writing retry, including after repeated empty reads. |
| Other unique keys, CHECK/FK failures, overflow or corrupt stored data | Propagate technical failure; do not retry or label it insufficient funds/replay. |
| Broad Spring lock exception without vendor evidence, driver query cancellation or connection failure after confirmed rollback | Conservative unavailability; these are not automatically classified as 1205/1222. |
| Interrupted thread or insufficient request budget | Stop admitting work, preserve interruption and return unavailability. |

`SqlServerFailureClassifier` follows causes and JDBC next-exception chains with cycle protection. Connection SQLState class 08, a different nonzero vendor code, or suppressed cleanup evidence disqualifies the transient classification. Existing store adapters remain responsible for recognizing only `uq_operations_transaction_id`; other uniqueness failures do not become duplicates.

## Settings and budget

| Setting | Default | Validation or responsibility |
| --- | --- | --- |
| `balance.retry.max-attempts` / `BALANCE_RETRY_MAX_ATTEMPTS` | 3 | 1–10 writing attempts; each recovery phase is also bounded by this count. |
| `balance.retry.budget-millis` / `BALANCE_RETRY_BUDGET_MILLIS` | 20000 | 10000–120000 ms; monotonic elapsed time includes lookup, preparation, connection waits, transactions, recovery and backoff. |
| `balance.retry.backoff-millis` / `BALANCE_RETRY_BACKOFF_MILLIS` | 50 | 0–1000 ms base; exponential full jitter gives 0–50 then 0–100 ms with default attempts. |
| Hikari acquisition timeout | 5000 ms | A new transaction is admitted only when at least acquisition + transaction allowance, 10000 ms, remains. |
| Transaction timeout and isolation | 5 s, READ_COMMITTED | Existing owned `TransactionTemplate`. |
| SQL lock timeout | 1000 ms | Hikari connection initialization executes `SET LOCK_TIMEOUT 1000`. |
| Native query timeout | 2000 ms | Existing JPA query hints in the account and operation adapters. |
| Driver login/socket timeout | 5 s / 5000 ms | Hikari datasource properties, alongside explicit integration JDBC settings. |

Binding invalid retry settings fails startup. Jitter waits outside transactions. Admission also reserves the maximum next jitter before sleeping; it does not start an attempt that cannot fit the remaining allowance. These are cooperative local-development defaults, not a hard network/commit deadline or measured production SLO. Manual pool/driver/transaction overrides require reviewing that admission allowance too.

## Verification and limits

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 157 real SQL Server tests**, with **zero failures/errors/skips**, in **43.636 seconds**. Fresh Surefire/Failsafe XML totals were checked.

Portable cases cover vendor-code chains and cycles, nonretryable defects, stable preparation, rollback-before-retry, concurrent-winner recovery, conflict, read retry, exhaustion, budget admission, interruption, rollback failure, uncertain commit with visible or repeatedly absent outcomes, and configuration binding. Existing credit/debit/transfer tests continue to cover terminal rejection, replay and unchanged financial write boundaries.

The compiler syntax-tree scan checked all 145 Java source files and found zero nested/local/anonymous types. Changed-document relative links and exact local credential scans of the run logs and reports passed.

Three new SQL Server cases distinguish different evidence:

- A real transfer encounters an actual blocked account and error 1222. At backoff, independent SQL finds zero operation/journal/posting rows. Releasing the blocker permits a second attempt using the identical prepared object. Exactly one operation, one journal and two postings commit; both balances reconcile independently.
- An isolated two-connection JDBC fixture deliberately inverts lock order and selects the coordinator transaction as the real SQL Server 1205 deadlock victim. Its earlier update is rolled back before retry; final values reflect one effect. This verifies coordinator/JDBC recovery, not a production account-lock inversion or a complete JPA transfer deadlock.
- A test-only transaction-manager wrapper throws after the real transfer commit. Recovery returns the stored response and HTTP replay agrees. Counts stay 1/1/2 and both balances reconcile. This simulates acknowledgement loss; it does not inject an actual network partition during commit.

Docker Desktop initially was stopped. Starting the existing SQL Server container first failed with startup memory-pressure diagnostics. A second start of the same container restored health without changing its image, volume, resource settings, schema or grants. Maven's first sandboxed invocation could not write cache locks; authorized verification then used the existing cache. An initial portable regression identified three old commit-failure event assertions, which were updated to require the new recovery transactions.

CBS16–19 broad duplicate/conflicting races across instances, opposite transfers, mixed-operation progress and additional commit/response fault cases remain open. Independent learner acceptance, authentication/ownership and performance gates remain separate. The existing SQL Server ARM64 emulation is unsupported by Microsoft. No schema migration, public API change, external service or production-readiness claim is introduced.
