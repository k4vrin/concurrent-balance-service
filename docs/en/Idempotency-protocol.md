# CBS03 durable operation identity protocol

[فارسی](../fa/Idempotency-protocol.md) · [ADR-002](Decisions.md#adr-002-idempotency-actors-and-redis) · [Requirements](Requirements.md)

Status: draft for joint review, 2026-10-22. Accepted business policies below retain their existing status. Identifier syntax, encoding, lifecycle and recovery mechanics are proposals, not accepted implementation. No schema, code or SQL Server test is introduced by this document. CBS04 must review the exact transaction/locking SQL before financial migrations.

## Promise and scope

A retained transaction ID identifies one immutable command and at most one committed business outcome. Matching retries return its original terminal response. A different command under that identity conflicts. This covers credit, debit, transfer and internal opening operations in one global single-tenant namespace. Balance GET does not reserve an operation ID. Distinct IDs are distinct attempts, not proof of distinct human intent.

SQL Server is the authority in V1. A later Redis hit can replay only a validated copy of a committed outcome. Redis loss cannot authorize a new financial effect. This is at-most-one committed effect per retained identity, not a promise of exactly-once network delivery.

## Identity and canonical command

Proposed transaction-ID syntax: 1–128 ASCII characters from letters, digits, `.`, `_`, `:`, `-`; case-sensitive exact identity. Reject whitespace rather than trimming it; reject null/blank/oversize/unsupported characters before reservation. Use the same comparison in the application and unique database key; SQL Server collation and trailing-space behavior require real tests. Account IDs now wrap UUID and share the descending unsigned application comparator; HTTP UUID parsing and SQL mapping/locking still require implementation. Internal opening IDs use the same uniqueness mechanism; a collision must never authorize replay of a different command.

Canonical command fields: encoding version, operation type, requested source/destination/target IDs in their distinct roles, positive rial amount, currency, and authenticated actor ID when authentication exists. V1 has the documented trusted local operator boundary, not a fabricated customer identity. Introducing actor binding later needs versioned compatibility rules for older records. Credit/debit funding accounts are selected by server policy; preserve the actual chosen counterpart and policy version in execution metadata. A retry does not select a new counterpart.

Proposal: deterministic length-prefixed UTF-8 fields with explicit null markers and a fixed field order. Encode the validated amount as its canonical base-10 integer value. JSON whitespace/property order and equivalent accepted numeric spellings must not change identity. Preserve canonical bytes plus a SHA-256 fingerprint and encoding version; compare the canonical command as well as the hash rather than treating a hash match as proof. Transaction ID is the lookup key; exclude retry time, trace ID, response timestamp, OTP secrets and other transport metadata. Never silently rewrite account IDs or change currency/amount units during canonicalization.

Store the original HTTP status, stable application code, response schema version, serialized response body and contract-relevant headers with the terminal outcome. Replay preserves original business-response content, including original balances if returned; it does not rerun a balance lookup. Per-request tracing and transport headers may differ. Keep the stored response format readable across deployments; contract upgrades cannot silently reinterpret old records.

## Processing sequence

1. Parse and validate request shape, IDs, positive rial amount, IRR and distinct transfer accounts. Authentication/current authorization is mandatory before disclosing replay once security exists; V1 remains local/trusted. These failures do not reserve an operation ID.
2. Construct the versioned canonical command. Perform an optional committed-outcome lookup. Existing identity: check current access and compare the command; match returns the stored outcome, mismatch returns conflict. Do not evaluate current funds, current counterpart selection or mutable business limits before this replay decision.
3. If no committed identity was found, begin a fresh local database transaction. Insert and explicitly flush a reservation in `operations` under the global unique transaction-ID constraint before acquiring account locks or writing monetary data. An initial PENDING representation is private to this uncommitted transaction. It is not a separate committed processing record or distributed lease. Readers must not use dirty reads.
4. After successful reservation, resolve participants and acquire every customer/system account lock in the agreed global order. Read current state under the CBS04 protocol. Requested identifiers in operations are audit/request data without account foreign keys, so a nonexistent account can still produce a rejection. Successful postings retain mandatory real-account references.
5. Evaluate business rules and calculate every projection delta with checked arithmetic before changing balances. Missing account, insufficient funds and resulting overflow produce a terminal REJECTED outcome with the original response, no journal/postings and no balance changes. Proposal for review: exceeding a mutable business transaction cap is also terminal rejection, evaluated only for a new operation. Numeric cap and public code remain open.
6. On success, persist one complete balanced journal, its immutable postings, all affected projections and terminal SUCCEEDED outcome together. Opening operations follow the same identity mechanism. Pre-render the stored response before commit so serialization failure cannot leave an unrecorded success response.
7. Commit only a terminal outcome. Return a rejection as a value across the transaction boundary so HTTP error mapping does not roll it back. A technical failure rolls back reservation and all financial writes together. Do not catch a persistence failure and continue in a failed/rollback-only transaction. No controller/network/cache call occurs inside the financial lock window.
8. Return the stored terminal response only after commit is confirmed. Later Redis population is best-effort after commit; a cache failure cannot change a committed result. If commit acknowledgment is lost, recover through the identity protocol and report temporary/uncertain outcome if recovery cannot finish within the deadline.

The writer must never commit PENDING. The proposed schema/transaction path must make this lifecycle explicit; row-level constraints alone do not prove the commit-time success/journal relationship. A committed nonterminal row is an invariant incident, not permission for another request to steal execution. Recovery of such corruption requires controlled investigation.

## Concurrent requests

| Step | Request A | Request B with the same ID |
| --- | --- | --- |
| 1 | Committed lookup finds nothing | Committed lookup may also find nothing |
| 2 | Wins reservation insert/flush | Attempts the same unique identity; contention is bounded |
| 3 | Evaluates and commits success or rejection | Must not perform financial writes without winning reservation |
| 4 | Returns its committed response | If identity insert loses with the expected duplicate-key error, abandon/roll back this transaction and start a fresh recovery read |
| 5 | No further effect | Committed command matches: replay. Differs: conflict |

A's reservation is not a durable win until commit. If A rolls back, B may acquire the identity and execute; there is then no earlier committed outcome to replay. Arrival order is not guaranteed. Exact blocking/error behavior and error classification must be tested on the chosen SQL Server/JDBC/JPA stack. Recognize the named transaction-ID uniqueness violation specifically, not every integrity error. A different constraint failure is not evidence of an existing operation.

If the recovery read finds no terminal record, never infer that the uncertain earlier request is safely gone. Re-enter the same unique-reservation protocol in a fresh transaction, or return temporary failure when the retry/deadline budget is exhausted. An unresolved earlier transaction still competes on that same identity. Recovery reads must reach the authoritative database, not a lagging replica.

## Failure and retry rules

| Boundary | Durable state / action |
| --- | --- |
| Invalid input or denied access | No operation reservation; return validation/access failure |
| Crash before commit, transaction rolled back | No operation/journal/postings/projection changes from this attempt; same ID may execute later |
| Committed rejection, then funds/account/policy changes | Replay original rejection; fresh attempt requires new ID |
| Committed success, response lost | Replay success; no new journal or funds check |
| Deadlock victim or explicitly classified transient rollback | Bounded fresh whole-transaction retry, same ID and canonical command; backoff outside transaction |
| Lock/connection timeout or uncertain commit | Do not assume rollback; resolve identity, use a fresh transaction, and preserve temporary/uncertain semantics |
| Retry budget exhausted | Proposed 503; keep same ID for recovery; do not claim no commit occurred |
| Integrity/programming error outside expected identity collision | Roll back; investigate, do not convert into a terminal business rejection or indiscriminately retry |

Retry attempt limits, backoff, lock timeout and total request deadline are explicit CBS04 configuration decisions, still open. No infinite retry and no automatic execution retry of committed business rejection.

## Retention, cost and review gates

Accepted by the user on 2026-10-23 for V1: retain committed terminal identity, canonical command and original outcome indefinitely; no operation cleanup job. Both success and terminal business rejection reserve the ID permanently. Validation failure and rolled-back transactions leave no committed reservation. Reversal/correction uses a new linked operation ID and preserves original replay. This accepts retention only; the remaining draft mechanics still require review. Redis TTL never controls SQL identity retention. Future archival must keep replayable response and uniqueness protection. A tombstone alone prevents re-execution but cannot reproduce the original response; replacing full outcomes with tombstones changes this contract and requires a separate decision.

Costs to measure: one operation row plus unique-index maintenance per terminal decision, canonical/response storage, duplicate contention and recovery reads. A rejection writes no financial journal, but still costs an operation commit. Admission/rate limits protect against floods of fresh IDs; idempotency does not. Future OTP intent uniqueness is independent of transaction-ID uniqueness.

Review before implementation: ID/canonical encoding and actor-version compatibility; private reservation/terminal commit representation; duplicate-key rollback/recovery; cap rejection classification; retention and storage; CBS04 lock/error/deadline choices. No numerical business cap is selected here.

Required real tests: two application instances with simultaneous identical/conflicting requests; winner rollback; deadlock/timeout and lost commit acknowledgment; serialization failure; rejection replay after state/policy changes; missing-account rejection despite absent account; restart and cache loss; byte-stable response and fingerprint version compatibility; no committed PENDING row; journal counts and independent balance reconstruction. Planned tests are not passing evidence.

## Primary references

SQL Server enforces UNIQUE through a supporting unique index, but that does not implement response replay: [unique/check constraints](https://learn.microsoft.com/en-us/sql/relational-databases/tables/unique-constraints-and-check-constraints). Transaction error handling must account for uncommittable transactions: [TRY/CATCH and transaction state](https://learn.microsoft.com/en-us/sql/t-sql/language-elements/try-catch-transact-sql). Deadlock victims require transaction-level recovery: [deadlock guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-deadlocks-guide). Consulted 2026-10-22; validate concrete behavior against the pinned server and driver before implementation acceptance.

## Accepted transaction-boundary mechanism, 2026-10-23

Use a nontransactional coordinator and Spring `TransactionTemplate` for each financial attempt, with REQUIRED propagation and an enforced no-ambient-transaction entry boundary. Return explicit Success/Rejected values; technical exceptions roll back. Perform retry, duplicate recovery and HTTP mapping outside the attempt. Never use a separate REQUIRES_NEW commit for reservation, rejection or successful financial outcome. Template completion confirms commit only when this no-outer-transaction condition holds. Enforce the boundary with an explicit guard or a separately proxied NEVER entry point; self-invocation must not bypass it. This accepts the mechanism, not SQL behavior or the entire protocol. [CBS04 draft](Transaction-protocol.md) supplies the next review artifact.

Account-ID amendment, 2026-10-23: canonical commands will encode parsed account UUIDs using UUID.toString; transaction-ID identity rules are unchanged. AccountLockOrder.DESCENDING defines application lock order, not canonical field order. Parsing/fingerprint code and SQL locking remain unimplemented.
