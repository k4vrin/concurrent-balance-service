> Current review disposition, 2026-10-23: CBS02–04 engineering review is closed for entry to CBS09. [The implementation contract](CBS09-contract.md) selects storage, lock SQL, constraints, retry defaults and authority. Earlier open-choice entries below are historical unless retained in that contract. Migration/entity/financial verification belongs to CBS09–20; no business DDL has been applied.

# CBS04 transaction and locking sequence

[فارسی](../fa/Transaction-protocol.md) · [Identity protocol](Idempotency-protocol.md) · [Ledger](Ledger.md) · [Business outcomes and commit boundary](Operation-outcomes.md)

Draft for joint review, 2026-10-23. TransactionTemplate with an enforced nontransactional coordinator was accepted today. The sequence, isolation/lock mapping and settings below remain proposals until reviewed and verified. No financial execution or business DDL exists. [Native JDBC review experiments](Persistence-review.md) now supply limited lock/comparison evidence.

## One attempt

1. Outside the transaction, validate immutable request shape and current access, canonicalize the command and check for a committed replay. Do not reevaluate mutable business policy on an existing outcome.
2. The coordinator rejects an ambient transaction. Execute a REQUIRED TransactionTemplate callback; each attempt therefore owns one physical transaction. Technical exceptions must escape it; any checked technical exception must also be translated or explicitly handled to guarantee rollback.
3. Reserve and flush the global operation ID before acquiring accounts. Expected identity collision exits/rolls back the callback before fresh recovery. Do not catch persistence errors and continue inside it.
4. Resolve the complete customer/system participant set, deduplicate it and lock existing accounts in one descending total order. AccountId wraps UUID; use AccountLockOrder.DESCENDING, comparing the high then low 64 bits unsigned, in descending order. Never lock source-first or discover additional participants after beginning an inconsistent lock sequence. Sequential locking calls must follow this application order; do not substitute UUID.compareTo, SQL Server GUID order or assume ORDER BY controls lock acquisition. Actual locking SQL still requires review/testing.
5. Proposed baseline: READ_COMMITTED plus explicit indexed locking account reads that acquire update/exclusive-compatible write-intent protection before returning balances and retain it through commit. Plain reads followed by later locks are insufficient. Map JPA pessimistic locking to actual SQL Server SQL and verify lock lifetime, waiting, missing-row behavior, deadlock and lock escalation on the pinned stack. Do not assume the annotation guarantees physical row-only locking. Account initialization/closure/repair and every other writer follow the reviewed protocol.
6. Recheck existence, currency, category, status and applicable business rules under the lock protocol. If any requested account is absent, commit a terminal rejection using request identifiers without account FKs. Unknown internal funding configuration is an infrastructure/configuration defect, not a fabricated customer business rejection. Snapshot participant selection and any mutable policy version consistently for this attempt.
7. Build the complete journal in memory, validate positive lines, at least two lines and exact per-currency balance, and calculate all checked projection changes. No balance mutation occurs before all checks pass. Validate against both customer nonnegativity and signed system-account bounds. Ordinary business rejection returns a stored Rejected value, without monetary writes.
8. Persist the successful journal, postings, every projection and stored serialized success outcome in the same transaction. Flush failures are technical failures and roll everything back. A rejection commits only the terminal operation and original response. Never commit a private PENDING reservation. No Redis, OTP delivery, Kafka send or external network request occurs while holding account locks.
9. After confirmed commit, the coordinator returns/maps the stored outcome. On an expected duplicate, recover in a fresh transaction. On a classified transient rollback, retry the entire attempt with the same ID/command and backoff outside a transaction. For uncertain commit, recover identity first; never infer absence of commit from a connection error or one empty lookup.

## Worked transfer

Let A = 10000000-0000-4000-8000-000000000001, B = f0000000-0000-4000-8000-000000000002 and funding F = 00000000-0000-4000-8000-000000000007. A transfer from A to B locks B then A. An opposite transfer also locks B then A, so it waits at the first shared account instead of deliberately acquiring the opposite order. This reduces this particular deadlock pattern; indexes, uniqueness checks and other database resources can still deadlock. Bounded whole-transaction retry remains necessary.

Credit to customer B with funding F locks B then F. Concurrent credits to different customers can contend on funding F; measure that shared-resource bottleneck without weakening atomicity or inventing unreviewed funding buckets.

## Error boundary and settings

Expected business rejection is a value, not a rollback-triggering exception. Unexpected FK/check failures are defects and roll back; only the named operation-ID uniqueness collision initiates duplicate recovery. Never broadly catch all DataIntegrityViolationException instances as replay.

Explicit configuration is required for maximum attempts, backoff/jitter, lock wait, statement/transaction timeouts and total request deadline. Values are not selected here. Pool/connection wait and backoff consume the total deadline too. A timeout must trigger transaction cleanup and fresh identity recovery before another attempt; it is not proof of rollback. Exhaustion returns temporary/uncertain failure, proposed HTTP 503, retaining the same ID.

## Gate before persistence

The UUID representation and application comparator are accepted and unit-tested; review SQL Server edition/version, actual locking SQL and connection/isolation settings; reservation lifecycle and response serialization; policy/participant stability; bounded retry/error classification; and runtime database permissions. Numeric business caps are now selected; see [Amount-limits.md](Amount-limits.md). Enforcement remains pending. Domain implementation in CBS05 can begin after conceptual review; migrations and durable operation implementation additionally need the pinned SQL choices and real tests.

Tests must cover both directions, identical/conflicting duplicates, independent-account progress, system-account contention, every write-boundary rollback, victim retry, uncertain commit and reconciliation. The written sequence does not certify these behaviors.

References: [Spring propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html), [TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html), [SQL Server deadlocks](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-deadlocks-guide). Framework/database mechanics require version-specific verification before acceptance.

[UUID bits and lock-order guide](UUID-lock-order.md) explains hexadecimal, two’s complement, both unsigned halves, comparator result signs and each test with a runnable example.

## SQL review update, 2026-10-23

[Concrete experiments and remaining gates](Persistence-review.md) record lock lifetime after result close, release on commit/rollback, independent existing-key progress and error 1222 leaving an active transaction with XACT_ABORT OFF. These native integer-key tests do not certify UUID/JPA account locking or the whole financial protocol. [Accepted limits](Amount-limits.md) require retained over-cap rejection.
