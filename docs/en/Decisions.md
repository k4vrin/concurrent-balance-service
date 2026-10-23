> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Architecture decision register

Status: ADR-001–005 detailed protocols remain Proposed, except the terminal business-rejection policy accepted below on 2026-10-22. ADR-007 accepts the V1 ledger design on 2026-10-22; implementation and financial evidence remain absent. Original register date: 2026-10-20. [فارسی](../fa/Decisions.md)

Configuration decisions are separate: [ADR-006](../adr/ADR-006-default-sqlserver.en.md) records the accepted removal of the SQL Server Maven/Spring profiles on 2026-10-22. [ADR-007](../adr/ADR-007-double-entry-ledger.en.md) accepts option E and supersedes the optional ledger clause in ADR-003. The detailed financial protocols below still require review.

## ADR-001: Durable correctness boundary

Scope amendment, 2026-10-22: V1 is an HTTP API for all four operations, built as vertical slices against SQL Server. [API-v1.md](API-v1.md) supersedes the service-only delivery sequence. Internal Java signatures may evolve. HTTP transport does not change the local database transaction boundary. Initial access is local/trusted; P3 remains the authentication and authorization gate before shared/external use. This scope choice does not mark financial ADRs accepted or code implemented.

Context: money movement, retries and later external systems must agree after failures. Proposal: a modular Spring Boot service with SQL Server as the financial authority, Java 21, Maven wrapper, Flyway and real SQL Server integration tests. SQL Server is the selected bootstrap persistence target following the discussion. Pin the server edition/version and verify it against the chosen JDBC/Flyway versions before accepting schema behavior.

Commit account projections, operation identity/outcome, one journal, its immutable postings and later outbox rows in one local database transaction. Lock all participating customer and system account rows in a consistent sorted order. Each operation must have an identifiable point at which its committed result becomes visible. No global service lock. Set bounded lock waits and distinguish business rejection from retryable infrastructure failure. Account read returns committed data; multiple separate reads do not promise a shared snapshot.

Alternatives: in-memory state loses restart durability; optimistic updates can work but require bounded whole-transaction retries; conditional SQL can work but needs an explicit two-account protocol. Redis locks introduce leases and another failure boundary without replacing database correctness. Costs: account contention, lock waits, connection usage, transaction-log/version-store work and operation-table growth. Acceptance evidence: direct SQL tests, simultaneous requests, opposite transfers and fail-before-commit rollback.

## ADR-002: Idempotency, actors and Redis

[CBS03 protocol draft](Idempotency-protocol.md) specifies the proposed reservation, canonical command, concurrency, replay and recovery flow for joint review. It does not change this ADR to Accepted.

Context: a client controls transaction IDs; a new ID can represent a genuinely new authorized operation. Idempotency cannot infer user intent or stop an authorized client from submitting new commands. Authorization, limits and confirmation address different threats.

Proposal: one globally unique transaction ID across credit/debit/transfer for the single-tenant service. Store canonical request fingerprint, actor binding and terminal outcome with the financial transaction. Distinct IDs for the same future transfer intent cannot execute it twice; add independent uniqueness on the executed intent ID in P4. Duplicate requests return the original business outcome, while payload mismatch conflicts. Authenticate and authorize the current caller before returning any replay; unknown ownership must not disclose another actor's result.

Rejection policy accepted by the user on 2026-10-22: well-formed, authorized requests rejected for business reasons persist a terminal rejection without balance changes, journals or postings. Retrying the same ID returns that rejection even after funds change. Syntax/authentication/authorization failures do not create financial operation rows. Technical failures before commit leave no terminal outcome; retries recover through database identity checks. Revalidate transient failures without silently converting them into permanent business rejections.

Redis stores only immutable terminal-response copies keyed by environment and transaction ID, with a fingerprint and schema version. On miss, expiry, corruption, mismatch or outage consult the database; never interpret cache absence as permission to debit. Only populate from a committed result. Any cache-hit path still checks current access. Terminal-result corrections require a version/invalidation policy before permitting such corrections. Database records or durable uniqueness tombstones outlive Redis TTL; deleting identity changes the retry guarantee and requires an ADR. No performance improvement is claimed before measurement.

Alternatives: Redis-only records leave a database/Redis commit gap; Redis-based execution leases can reduce contention but cannot be the safety authority. Costs: memory, serialization, network call, stale schema handling and another dependency. Gate: flush Redis after commit, crash before cache population, duplicate requests on two instances and ownership attacks all preserve one financial effect.

## ADR-003: Database constraints before schema

Proposal: review [Database-review.md](Database-review.md) before V1. Map each invariant to its transaction rule, database safeguard, attack/race test and cost experiment. Keep basic positive-amount, category-aware balance-bound, mandatory-reference and identity constraints even if they have a measured cost; investigate the bottleneck before weakening financial invariants.

Ordinary row CHECK constraints cannot enforce the entire two-account transfer or a sum over postings. Enforce cross-row balance through the transaction/posting path, permissions and reconciliation. Superseded scope clause, 2026-10-22: accepted [ADR-007](../adr/ADR-007-double-entry-ledger.en.md) requires double-entry history from V1. Use the four-table model, funding/clearing counterparts, opening journals and independent reconciliation in [Ledger.md](Ledger.md). Prohibit unaudited alternate balance writes. No percentage overhead is known yet.

## ADR-004: Transaction-bound confirmation

Context: retry protection does not establish that the account owner approved this particular recipient and amount. Proposal: after P3 authenticated access, create an immutable transfer intent bound to actor, source, destination, amount, currency, expiry and a server-generated ID. Show the significant details to the user before confirmation. Changing any detail requires a new intent and code. Preparing an intent does not reserve money.

Proposed endpoints: `POST /transfer-intents`, `POST /transfer-intents/{id}/challenge`, `POST /transfer-intents/{id}/confirm`, `GET /transfer-intents/{id}`. Challenge issuance and resend are rate-limited and idempotent as appropriate; resend invalidates the previous generation. Confirmation includes a transaction ID and code, while the immutable intent provides financial fields. No alternate public transfer route bypasses confirmation. Credit/debit retain privileged role checks.

Store an HMAC of a cryptographically generated short-lived random code with a secret held outside the database. An ordinary unsalted hash is insufficient protection for a short numeric code against offline guessing. Use a constant-time comparison, bounded attempts and resend limits; final policy values and delivery channel require a security review. Never log or return a live code in production. Start with a fake delivery adapter confined to tests/local development; this does not prove real second-factor delivery or phishing resistance.

Lock the intent before its accounts in all confirmation paths; acquire accounts in sorted order. Verify actor, fingerprint, current intent state, expiry and code. Successful code consumption, intent completion, transfer journal/postings and projections, unique intent linkage, operation outcome and later outbox event commit together. Concurrent confirmations under different transaction IDs still execute the intent once. Keep network delivery outside money locks. Invalid-code attempt increments must commit even when the API reports rejection; avoid throwing inside a transaction in a way that rolls them back. Serialize resend, attempt counting and confirmation on the intent. A business rejection after a valid code is proposed as terminal and consumes the intent; the user must prepare a new intent for a new attempt.

For a completed intent, authenticate/authorize and match the original operation identity/fingerprint before replaying its result; do not require a fresh valid OTP to retrieve an already-completed result. Do not allow this recovery path to execute a new operation. A status read allows recovery when the original transaction ID is unavailable.

Costs: extra round trips, state, delivery reliability, abuse controls and more difficult failure semantics. Gate: stolen/mismatched code, changed destination, expired code, resend race, brute-force counters, two confirmations, rollback, lost response, unauthorized replay and bypass tests. Basis: [OWASP transaction authorization](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html).

## ADR-005: Events first, CDC later

Proposal: append versioned, stable-ID events to an outbox in the same transaction as successful operations, journals, postings and account projections. A polling publisher sends to Kafka and marks progress only after broker acknowledgement. A crash between acknowledgement and marking can duplicate an event. A history consumer atomically inserts its processed-event identity and history effect in its own database transaction, then permits offset commit. Kafka transactions alone do not atomically commit an external database.

Events represent committed facts, not commands to move the balances again. Start with operation-centric history keyed by operation ID; account-wide order across multiple operations is not promised by this key. A later balance projection needs explicit per-account versions and ordering/reconciliation rules. Define retries, backoff, dead-letter inspection and recovery without skipping financial evidence silently.

At P7 evaluate Debezium on outbox inserts. Record exact SQL Server edition/version, connector and capture adapter compatibility, privileges, native CDC, SQL Server Agent/capture jobs and change-table retention, snapshot recovery, connector offset/schema-history storage, connector permissions and licensing where applicable. Never assume a generic connector can run against the chosen local SQL Server image. Transition with polling quiesced and an explicit cutover boundary; consumer dedup remains required for overlap/recovery. Polling is the fallback. Evidence: broker outage, duplicate delivery, restart and replay tests. See [Kafka semantics](https://kafka.apache.org/41/design/design/#message-delivery-semantics) and [Debezium outbox](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html).

## ADR lifecycle and template

Use stable IDs. Proposed → Accepted only after alternatives and the phase's required evidence are reviewed. Accepted does not mean implemented. Record implementation commit, measured results, date and limitations separately. Supersede old decisions with a linked replacement rather than rewriting history. Update both languages in the same PR.

For each new ADR record: ID/title; status/date/owner; context and constraints; invariant/threat; alternatives; chosen boundary; transaction/locking/failure behavior; qualitative cost hypothesis; measurement method and baseline; actual results or “not measured”; acceptance tests; rollback/migration; links to PR, schema and both language versions. Unknowns stay explicit.

## Accepted monetary representation, 2026-10-22

The user selected rial IRR amounts and accepted Java `long` for posted amounts and balances. One unit means one rial. Use SQL Server `BIGINT` and explicit API units such as `amountRial` and `balanceRial`; do not interpret them as ISO hundredths. Fractional-rial input is invalid before operation creation. The user accepted a separate business transaction limit; its numeric value and any business balance limit remain unresolved. The type maximum is not the business maximum.

Use checked addition/subtraction, including system-account projections, and reject overflow without partial monetary effects. Validate journal/reconciliation totals using an exact wider accumulator before checking projection bounds. `BigDecimal` is also exact and remains appropriate for rates, fees and fractional intermediate calculations; a reviewed rounding/residual policy must precede posting those calculations.

Interview rationale: integer units directly encode V1's allowed rial values. Currency precision and storage type are separate decisions. `long` does not provide concurrency safety; the reviewed transaction/locking protocol does. Revisit decimal ledger storage if fractional-unit balances or accruals, or amounts beyond BIGINT, become requirements.

Future FX can retain integer posted units with an explicit unit policy per currency, decimal rates and balanced postings independently per currency. Preserve the accepted quote and rounded amounts for replay. Accounts, FX position/clearing, liquidity, settlement and rounding rules require CBS38 design; this note implements none of them. Monetary policy is accepted design, not measured or implemented financial behavior.

Retention policy accepted on 2026-10-23: committed operation IDs, canonical commands and original terminal responses are retained indefinitely in V1, for success and business rejection alike. Validation failure or rollback leaves no committed reservation. Corrections use new linked IDs; Redis TTL does not release SQL identity. Future archival must preserve uniqueness and original-response recovery. See [CBS03 retention](Idempotency-protocol.md).

## Accepted transaction-boundary mechanism, 2026-10-23

Use a nontransactional coordinator and Spring `TransactionTemplate` for each financial attempt, with REQUIRED propagation and an enforced no-ambient-transaction entry boundary. Return explicit Success/Rejected values; technical exceptions roll back. Perform retry, duplicate recovery and HTTP mapping outside the attempt. Never use a separate REQUIRES_NEW commit for reservation, rejection or successful financial outcome. Template completion confirms commit only when this no-outer-transaction condition holds. Enforce the boundary with an explicit guard or a separately proxied NEVER entry point; self-invocation must not bypass it. This accepts the mechanism, not SQL behavior or the entire protocol. [CBS04 draft](Transaction-protocol.md) supplies the next review artifact.

## ADR-008: Accepted application architecture, 2026-10-23

[ADR-008](../adr/ADR-008-application-architecture.en.md) accepts the modular monolith, feature boundaries and selective ports/adapters. [Architecture](Architecture.md) records the examples and planned tree. Single-database CQRS was subsequently accepted in ADR-009; advanced variants and other suggestions retain their stated status. No financial persistence is implemented by this acceptance.

## ADR-009: Accepted single-database CQRS, 2026-10-23

[ADR-009](../adr/ADR-009-single-database-cqrs.en.md) accepts separate command/write models and query projections on the same SQL Server database, preserving atomic synchronous balances. No command bus, separate read store or event sourcing is required. Implementation and database evidence remain pending.

## Accepted account identity and ordering, 2026-10-23

AccountId wraps UUID, independent of transaction-ID identity. AccountLockOrder.DESCENDING compares the UUID high/low halves unsigned, descending, for all account-writing paths. UUID wrapper and comparator are implemented and unit-tested; no database locking is implemented. Sort/deduplicate the full participant set and perform sequential locking lookups in the financial transaction. Do not substitute Java natural UUID order, SQL GUID order or bulk-query result order as a lock-acquisition guarantee. SQL mapping, index layout, parsing and real contention tests remain pending.
