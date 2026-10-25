> Current CBS13 update, 2026-10-25: [Durable debit](Debit-execution.md) is implemented alongside opt-in credit and authoritative balance lookup. Transfer remains unavailable until CBS14. Full verification passed 351 portable plus 134 real SQL Server tests, zero failures/errors/skips. Earlier pending/unavailable statements below are historical.

> Current CBS11–12 credit update, 2026-10-24: [Durable credit](Credit-execution.md) is now active with explicit policy enablement. It commits balanced history/projections/outcome and returns retained success/rejection bytes; debit/transfer keep their 503 fallbacks. Earlier unavailable/adapter-only statements below are historical.

> Current persistence update: [CBS10 evidence](Persistence.md) now verifies JPA mappings, committed opening fixtures and the SQL Server balance route. Mutations remain unavailable. Earlier all-route-503 and schema-only statements below are historical.

# V1 HTTP API contract

[فارسی](../fa/API-v1.md) · [Roadmap](Roadmap.md) · [Business outcomes and commit boundary](Operation-outcomes.md)

Follow the [operation walkthrough](Operation-flow.md) for a complete request-to-response example and a source map.

Scope selected by the user on 2026-10-22: the first functional version delivers all four balance operations through a JSON HTTP API backed by SQL Server. Endpoint shapes below are proposed until reviewed in CBS01/CBS23. The CBS23 adapter now exposes these routes with an unavailable fallback; no financial execution is implemented. See the CBS23 amendment below. V1 means the first functional API release; it is separate from Flyway migration `V1` and any Git tag.

## V1 routes

| Method | Route | Request |
| --- | --- | --- |
| POST | `/api/v1/accounts/{accountId}/credits` | `transactionId`, `amountRial`, `currency` |
| POST | `/api/v1/accounts/{accountId}/debits` | `transactionId`, `amountRial`, `currency` |
| POST | `/api/v1/transfers` | `transactionId`, `sourceAccountId`, `destinationAccountId`, `amountRial`, `currency` |
| GET | `/api/v1/accounts/{accountId}/balance` | Account ID in the path |

Accepted on 2026-10-22: V1 accepts IRR only and posts rials. One stored unit equals one rial; use Java `long` and SQL Server `BIGINT`. Proposed request field `amountRial` and balance field `balanceRial` state the unit explicitly; 150000 means 150,000 rials, with no multiplication by 100. Reject fractional-rial inputs before creating an operation record; do not truncate or silently round them. Positive amounts must fit the signed-long range. Business transaction/balance limits remain to be reviewed in CBS01. Use checked arithmetic for projection changes. `BigDecimal` is reserved for future rates and fractional calculations with an explicit rounding policy before posting. This is accepted design, not implemented behavior. Mutations share the global transaction-ID namespace with operation-type and payload conflict detection. Syntax failures do not reserve an operation ID. Account initialization remains documented development/test setup, separate from these four routes.

Successful mutations return HTTP 200 and an explicit operation result containing its transaction ID, operation type, currency and committed outcome data. Replay returns the stored original result even after later operations change balances. Balance lookup returns the committed balance and currency. Specify the exact DTO fields, content types and examples during CBS23 before implementing the paths.

Proposed errors use a consistent ProblemDetail response with a stable application `code`: HTTP 400 for malformed input, invalid IDs/amounts, unsupported currency or self-transfer; 404 for an unknown account; 409 for transaction-ID payload conflict, insufficient funds or balance overflow; 503 for transient infrastructure unavailability or exhausted transient retries. Final codes and uncertain-commit recovery are reviewed in CBS01/CBS03/CBS23. A 503 or lost response must not claim that no commit happened. Retry with the same transaction ID to recover the durable outcome. Stack traces, credentials and internal SQL are excluded from responses.

## Delivery and acceptance

The existing Java interface is bootstrap scaffolding, not the V1 deliverable or a fixed public API. Keep financial transaction logic behind thin feature-based controllers with constructor injection. Internal service types and signatures may evolve to express outcomes and business failures; preserve the four operation semantics and document any deliberate departure from the original challenge's Java signatures. Do not remove the interface merely to move transaction logic into controllers.

Introduce the HTTP contract, DTO validation, error mapping and focused adapter tests at CBS23, immediately after CBS05. Build each durable operation as an HTTP-to-SQL Server slice at CBS10–15. No fake successful money response is an accepted implementation. Retain service-level concurrency and direct-SQL tests, and add real HTTP integration coverage for balance, mutations, replay, conflict, validation and failure rollback. The CBS20 core gate includes all four working routes and a reproducible Docker-backed API runbook with request/response examples.

V1 is initially a local development API on the existing loopback binding, with a trusted operator threat assumption documented explicitly. JWT, account ownership and role enforcement remain CBS24–25/P3, before shared or external access. V1 must not be described as a secured customer-facing service. Authentication failures later map to 401, authorization failures to 403, with current authorization required before replay. OTP remains P4; after confirmation is introduced, direct transfer routes must not bypass it. Redis, Kafka and CDC remain later stages.

## Accepted option E scope, 2026-10-22

The four public routes stay unchanged under [option E](Ledger.md). Each successful mutation commits one balanced journal, its postings, all affected projections and its stored outcome together. Rejections have no journal; replay returns the original response without more postings. The server selects the funding/clearing counterpart for credit/debit. Nonzero initialization is an internal opening operation, not an extra public route. Balance reads use committed synchronous projections. Exact DTO fields remain CBS23 work; the ledger does not require a new public endpoint.

## Accepted terminal business-rejection policy

Selected by the user on 2026-10-22. A well-formed, authorized request rejected for insufficient funds stores its transaction ID, canonical fingerprint and original rejection response in `operations`, with no balance change, journal or postings. Identical retries return that rejection even if funds later become sufficient. A new financial attempt uses a new transaction ID. A different payload under the retained ID conflicts and does not replace its original outcome.

Example: `debit-001` requests 50 when the balance is 30 and is rejected. After a separate credit raises the balance to 100, replaying `debit-001` still returns the original insufficient-funds rejection. A new ID permits a fresh funds check. This makes the business decision durable despite having no monetary effect. Syntax/access failures do not reserve financial identity. Technical failure before commit does not store a terminal rejection; uncertain commit must be recovered with the same ID. SQL Server is the durable authority in V1; Redis is a later cache with database fallback. This policy is accepted design, not implemented behavior; detailed storage, locking and HTTP error mapping still need review.

[Requirement and failure matrix](Requirements.md): reviewed cases, planned evidence and unresolved policies.

## CBS05 typed mutation commands, 2026-10-23

Implemented application inputs: `CreditCommand(TransactionId, AccountId, Money)`, `DebitCommand(TransactionId, AccountId, Money)` and `TransferCommand(TransactionId, AccountId sourceAccountId, AccountId destinationAccountId, Money)`. These immutable records live in `operation.application`; they are independent of JSON DTOs and persistence entities. Each requires non-null IDs and amount, positive IRR and, for transfer, distinct account UUID values. A package-private CommandValidation helper owns the common amount rule without changing Money's support for zero/signed values and other currencies.

Example, assuming existing typed sourceId and destinationId:

```java
var command = new TransferCommand(
        new TransactionId("transfer-001"), sourceId, destinationId,
        new Money(100, Currency.getInstance("IRR")));
```

Constructing a command does not reserve transaction identity or move money. The future adapter/coordinator must construct it before reservation. Missing fields currently throw NullPointerException; nonpositive/unsupported amounts and self-transfers throw IllegalArgumentException. Equality of AccountId values detects self-transfer even with separate wrapper instances. The original BalanceService interface remains unwired scaffolding; no handler/API implementation is registered by this increment. Credit/debit commands contain only the customer account; funding selection remains server-owned in the future execution path.

Account existence, authorization, sufficient funds and resulting projection overflow are not checked by these constructors. A well-formed mutation naming a missing account still needs the accepted terminal rejection path. JSON fractional/out-of-long amounts and malformed UUID text must be rejected by the future transport parser; a long-backed Money cannot itself represent those invalid JSON inputs. Long.MAX_VALUE is accepted at this representation boundary; numeric business caps and their mutable-policy/replay classification remain unresolved and must be reviewed before financial execution. No cap is inferred from the type limit.

Java 21.0.11 Maven verify passed 128 tests with zero failures/errors/skips in 8.736 seconds. Thirteen new command cases cover positive representation boundaries, nonpositive amounts, unsupported currency, missing fields, UUID-value self-transfer, direction/identity preservation and the absence of reservation during construction. No SQL Server or financial HTTP evidence. Next CBS05 increment: explicit business rejection/success outcome types and their boundary with technical failures, before HTTP adaptation. Task acceptance remains open.

## Supported currency policy amendment, 2026-10-23

At the user's request, supported currencies are no longer hardcoded inside CommandValidation or the command constructors. `CommandValidation.requirePositiveAmount` checks only presence and positivity. CreditCommand, DebitCommand and TransferCommand preserve the Money currency for a separate `SupportedCurrencies(Set<Currency>)` policy. This supersedes the earlier command-constructor IRR restriction. The initial V1 deployment policy remains IRR; a set-based validator does not add FX conversion or define units for additional currencies.

SupportedCurrencies defensively copies the supplied set and exposes `requireSupported(Currency)`. Any supplied currencies can be enabled; none is built into production validation code. Null sets/members/currency are rejected, and an explicitly empty set disables all currencies. Changing the source set cannot change an existing policy snapshot. Future configuration or a database adapter can supply a new snapshot without putting policy in command payloads or adding database reads to value constructors. No loader, database table or runtime bean is implemented yet.

Illustrative composition with a supplied policy set, before execution:

```java
var policy = new SupportedCurrencies(currenciesFromConfiguration);
policy.requireSupported(command.amount().currency());
```

A future coordinator must resolve an existing terminal outcome before reevaluating this mutable admission policy. Matching retries replay their original result even if that currency has since been disabled. For a new operation, evaluate support using one consistent policy snapshot before reservation; valid syntax and supported-by-current-policy are separate checks. Database loading failures are technical failures, not an empty policy or a terminal unsupported-currency decision. The loader/refresh consistency protocol remains future design and requires integration tests.

Java 21.0.11 Maven verify passed 137 tests with zero failures/errors/skips in 7.565 seconds. Nine policy cases cover multiple allowed currencies, rejection outside the set, a set without IRR, empty/null sets and immutable snapshots. The 13 command cases now verify currency preservation instead of a hardcoded unsupported-currency rejection. HTTP, replay ordering and database policy loading remain unimplemented; this is portable calculation/validation evidence.

## CBS23 HTTP foundation, 2026-10-23

The four routes above now have thin controllers, transport DTOs and ProblemDetail mapping. They delegate to `BalanceOperations`, a typed application boundary separate from the original `BalanceService` bootstrap interface. Production composition currently supplies an explicit unavailable implementation. Every structurally valid request reaches that boundary and receives HTTP 503 `OPERATION_UNAVAILABLE`; it neither stores identity nor reads/writes money. Test doubles exercise mapping only. Durable handlers replace this fallback in CBS10–15. This supersedes earlier statements in this document that no HTTP adapter exists; financial execution remains unimplemented.

POST requests use `Content-Type: application/json`; success uses `application/json`, errors use `application/problem+json`. Requests require every field in the route table. Transaction IDs are JSON strings of 1–128 ASCII letters, digits, `.`, `_`, `:` or `-`, preserved exactly and case-sensitively. IDs are supplied only in the body, with no header alias or generated default. Account IDs require the full UUID group shape, case-insensitive hexadecimal; shortened UUID groups are invalid. Transfer endpoints compare parsed UUID values to reject self-transfer.

`amountRial` must be a positive JSON integer token in the signed-long range. Decimal tokens including `1.0`, exponent notation, numeric strings, booleans, nulls and out-of-range numbers fail with 400 before application invocation. `Long.MAX_VALUE` passes representation validation; it is not an approved business cap. Unknown properties, duplicate keys, trailing JSON values, absent/null fields and malformed JSON fail with 400. Transport strings never coerce numeric or boolean tokens. Currency must be an exact uppercase ISO code recognized by `java.util.Currency`. Currency syntax differs from admission policy: the future replay-aware coordinator must resolve existing identity before applying `SupportedCurrencies` to a new operation. The fallback does not pretend to enforce a configured currency policy.

Initial deployment still admits IRR only when durable execution is wired. The names `amountRial` and `balanceRial` describe that deployment. Recognizing other ISO codes in transport does not enable their execution or define their units. Review units and the public contract before enabling another currency. No currency loader or hardcoded supported set is added by CBS23.

The minimal success DTO contains exactly `transactionId`, `operationType`, `currency`, `status`. It deliberately contains no current balance or journal ID. Illustrative contract output supplied by a test double, not a working financial request:

```json
{"transactionId":"Tx-001","operationType":"CREDIT","currency":"IRR","status":"SUCCEEDED"}
```

The balance DTO contains `accountId`, `balanceRial`, `currency`, for example `{"accountId":"00000000-0000-0000-0000-000000000001","balanceRial":150000,"currency":"IRR"}`. It must come from a committed projection in CBS10. No missing account is converted to zero.

| HTTP | Stable code | Boundary |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Parsing, required fields, positive/range rules, currency syntax, self-transfer; no application invocation |
| 400 | `UNSUPPORTED_CURRENCY` | New-operation admission in the future application coordinator |
| 404 | `ACCOUNT_NOT_FOUND` | Balance lookup failure or committed mutation rejection |
| 409 | `TRANSACTION_ID_CONFLICT` | Existing identity with different canonical input; no replacement outcome |
| 409 | `INSUFFICIENT_FUNDS`, `BALANCE_OVERFLOW` | Committed terminal mutation rejection |
| 503 | `OPERATION_UNAVAILABLE` | Unwired handler or explicitly classified infrastructure failure; commit may be unknown |
| 500 | `INTERNAL_ERROR` | Unexpected defect; outcome unknown, no automatic conversion to business rejection |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED` / `NOT_ACCEPTABLE` / `UNSUPPORTED_MEDIA_TYPE` | HTTP negotiation |

Problems have the default `about:blank` type, which the current serializer omits from JSON, HTTP `status`, standard `title`, sanitized `detail`, request `instance` and the extension `code`. A mutation rejection also contains `transactionId`, `operationType`, `currency`. Example body for a supplied committed debit rejection:

```json
{"title":"Conflict","status":409,"detail":"Available funds are insufficient.","instance":"/api/v1/accounts/00000000-0000-0000-0000-000000000001/debits","code":"INSUFFICIENT_FUNDS","transactionId":"Tx-001","operationType":"DEBIT","currency":"IRR"}
```

A returned `MutationResult` pairs original outcome with currency. The application must expose it only after confirmed commit, preserve the original serialized response including problem instance for replay, and never derive currency from changed retry policy. The current mapper is deterministic for the same supplied result and route; this is not durable replay. Rejection values map normally rather than being thrown to roll back an intended rejection record. `OperationFailure` distinguishes nonterminal conflict/admission/unavailability and missing balance lookup. SQL exception classification, whole-transaction retry and uncertain-commit recovery remain CBS03–04/CBS11–19 work. A 503/500 or lost response must use same-ID recovery and cannot establish that no commit occurred.

The strict Jackson settings apply to the application's JSON mapper, including unknown-field, duplicate-key and trailing-token checks. The exception advice is global because method/media negotiation can fail before controller selection. No raw parser, domain or SQL exception messages reach problem responses. Authentication, ownership, limits, migrations, funding selection, posting/projection writes and durable serialization are not implemented by this adapter. The trusted loopback deployment and CBS24–25 access gate still apply.

See [HTTP adapter evidence](HTTP-adapter-evidence.md) for verification and the next track step.

## Typed mutation response bodies

The three mutation controllers and OperationResponses mapper return `ResponseEntity<OperationResponseBody>`. This sealed interface permits the success record `OperationResponse`, final `OperationProblem` and retained-byte `StoredOperationResponse`. OperationProblem extends Spring ProblemDetail, preserving standard problem fields and top-level extension properties. It copies the existing rejection problem before adding transaction metadata. No wrapper object or JSON type discriminator is added. Framework/admission/technical exceptions still use the separate exception advice; the sealed family describes normal mutation returns. Business rejections remain returned values, preserving the future post-commit mapping boundary.
