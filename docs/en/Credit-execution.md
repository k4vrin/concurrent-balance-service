> CBS15 update, 2026-10-29: mutations now use [bounded whole-transaction retry and conservative uncertain-outcome recovery](Retry-execution.md). Earlier statements below that retry is unimplemented describe the previous checkpoint. `execute` remains a single-transaction primitive; mutation handlers use `executeRecovering`.

# Durable credit execution and retained responses

## Smoke assembly and current verification, 2026-10-25

Removed all production `@Profile("!smoke")` exclusions. A standalone `HttpSmokeConfiguration` in test sources imports only the HTTP boundary; the test checks that financial/persistence beans are absent even with execution enabled. Production coordinator and policy tests now verify normal dependency/property behavior independently of the test profile. The [class walkthrough](Credit-class-walkthrough.md) explains all participating classes, funding, identity and success/failure/replay flows.

The corrected assembly passed all 306 portable tests with zero failures/errors/skips. The attempted SQL Server phase did not complete: first the explicit test environment was missing, then the configured test connection was refused. The existing stopped container was started without image/volume/settings changes, but exited with status 114 during startup. No current SQL Server pass is claimed; the earlier 305+120 result below predates this smoke refactor. The initial standalone assembly attempt failed because `@TestConfiguration` added the main application; it was corrected to a standalone test `@Configuration`. Earlier sandbox runs also encountered Mockito attachment restrictions. At the user's request, further testing stopped. No database runtime guarantee or completed retry/concurrency gate is inferred from the portable pass.

[فارسی](../fa/Credit-execution.md) · [Class walkthrough](Credit-class-walkthrough.md) · [Coordinator](Operation-coordinator.md) · [Policy setup](Execution-policy.md) · [Persistence](Persistence.md) · [API](API-v1.md)

CBS11 and the first CBS12 replay/conflict slice now have a real credit path. With `balance.execution.enabled=true` and valid policy inputs, POST `/api/v1/accounts/{accountId}/credits` executes against SQL Server. With the switch disabled, credit retains the 503 fallback. The standalone HTTP smoke assembly also retains this fallback because it imports no financial services. Debit and transfer remain unavailable. Balance lookup remains the existing committed projection query. The API is still intended for trusted local access; authentication and ownership are not implemented.

## Follow a 100-rial credit

Start with an existing active zero-balance customer C and a separately provisioned active synthetic FUNDING_CLEARING account F in IRR. The configured `FundingSelection` chooses F; the caller supplies only C, amount, currency and transactionId. No public account creation or external bank funding is implied.

| Component | Job in this request |
| --- | --- |
| AmountRequest / CreditCommand | Strict HTTP parsing and immutable positive request shape |
| CanonicalRequest / CanonicalFingerprint | Versioned caller intent, independent of the ID lookup key and current policy |
| ExecutionPolicy / AmountLimits / FundingSelection | Startup policy snapshot, inclusive caps and server-selected counterpart |
| CreditHandler / CreditStore | Application flow and persistence port |
| OperationCoordinator | Own REQUIRED/READ_COMMITTED transactions, reject ambient transactions, return after commit |
| JpaCreditStore / AccountStore | Reservation, stored lookup, ordered native locks and actual SQL/JPA writes |
| Journal / Posting / Account | Balanced domain history and checked complete proposed projections |
| CreditResponseEncoder / JsonCreditResponseEncoder | Serialize the original public response before completion |
| StoredResponse / MutationResult / StoredOperationResponse | Defensive retained bytes, application outcome and exact HTTP delivery |

1. Canonicalize the credit with an explicit absent actor binding for the current trusted-local scope. In an owned transaction, look up the transaction ID before consulting current currency, caps or funding. Compare the full canonical bytes; the SHA-256 companion alone cannot authorize replay. Unknown stored formats, committed PENDING or inconsistent retained data fail closed.
2. If terminal data exists, return its original outcome and response. A different canonical request produces 409 TRANSACTION_ID_CONFLICT without another effect. An existing outcome is not reexecuted after policy or account changes.
3. For a new identity, reject an unsupported current currency with 400 and no operation. Select the retained funding counterpart and allocate operation/journal UUIDs. Funding must differ from the customer. The immutable startup policy is retained for this attempt; no hot reload or general retry loop exists.
4. Start the writing transaction and reserve the global ID before account locks. A named `uq_operations_transaction_id` 2627/2601 collision throws out of this transaction. Only after rollback does the handler perform a fresh committed-result lookup; other integrity errors are not duplicate recovery.
5. An amount above the transaction cap commits TRANSACTION_LIMIT_EXCEEDED with no journal/projections. This check precedes account lookup, so it takes precedence over missing-account rejection. Otherwise lock the full C/F set in deduplicated descending application UUID order with UPDLOCK/HOLDLOCK.
6. Validate internal funding existence, active status, category and currency. Invalid funding is a technical defect and rolls back. A missing customer commits ACCOUNT_NOT_FOUND without monetary writes. Inactive, wrong-category or currency-incompatible existing participants currently fail technically rather than inventing an unreviewed terminal rejection code.
7. Check customer balance room before addition: `current > customerCap - amount` means BALANCE_LIMIT_EXCEEDED. The operands make this comparison safe without overflowing a proposed sum; equality is allowed. Then construct funding DEBIT 100/customer CREDIT 100 and call Journal.projectBalances. Checked funding arithmetic still applies; overflow is technical rollback, not a retained success/rejection.
8. Append one header and two immutable lines, apply both managed account projections and flush. Serialize success, store the terminal operation and exact version/status/content-type/body envelope, then commit. Every write shares the same physical transaction. No monetary state escapes before all projections have been validated. A terminal business rejection stores only its outcome and response.
9. After successful commit, return the retained bytes. Under these category-normal balance conventions, C increases by 100 and a debit increases F by 100. Equal debits/credits balance the journal; funding is not an external bank account balance or proof of incoming cash.

## Preserve the original public response

`MutationResult` keeps its existing two-argument constructor for unwired/test-double outcomes and adds optional `StoredResponse`. The envelope owns defensive copies of its bytes and supports format 1 with success `200`/`application/json` or terminal `4xx`/`application/problem+json`. `JsonCreditResponseEncoder` uses the public mapper, retains the problem instance path for credit rejections and validates recovered JSON against the stored operation metadata.

The sealed `OperationResponseBody` now also permits `StoredOperationResponse`. Its RawValue serialization emits the retained JSON rather than rebuilding an object under current mapping rules. Both the first HTTP result and sequential replay are compared byte-for-byte with the database body in the integration tests. This preserves status, currency, reason and problem instance even after policy changes. StoredResponse equality/hash code compare byte contents across independent loads. Portable codec tests reject malformed JSON and metadata/outcome mismatch. Schema and V1 migration are unchanged.

## Failures, time bounds and remaining recovery

The coordinator now has a 5-second transaction timeout; native credit/account queries use a 2-second query timeout. These are transaction/statement controls, not a guaranteed end-to-end deadline. Database/transaction exceptions are exposed as 503 OPERATION_UNAVAILABLE after cleanup, with their causes retained internally; raw SQL messages are not sent to clients. Unexpected invariant/overflow failures remain technical failures, generally 500 through the existing advice. A timeout, connection error, 500/503 or lost response must be recovered with the same transaction ID.

Only expected identity collision currently initiates automatic fresh recovery. There is no general deadlock-victim retry/backoff, uncertain-commit recovery loop, total request budget or configured session lock-wait enforcement yet. The broader mixed-operation concurrency/reconciliation gates remain open. No production guarantee follows from one race test.

## Measured verification, 2026-10-24

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed 305 portable plus 120 SQL Server tests, zero failures/errors/skips, in 26.840 seconds. Fourteen credit integration cases add actual HTTP success/replay/conflict, decimal journal balance and customer/funding reconstruction, original rejection after policy/account change, unsupported-currency nonreservation, funding defects, and rollback after history/projection/outcome writes.

One test closes a real application context and its Hikari pool, creates another context/pool and replays the committed outcome; it is not a separate JVM or server restart. One synchronized two-worker race observes the named uniqueness collision and one operation/journal/two postings. It does not establish the full cross-instance or mixed-operation concurrency gate.

The overflow case injects only a Long.MAX_VALUE locked funding domain snapshot into the real reservation transaction and verifies full rollback. It does not commit an unreconciled balance or claim a SQL arithmetic overflow. Initial fault-injection assertions expected the inner exception directly; Spring translated it at the repository boundary. The final assertions verify the unavailable classification, original injected cause and unchanged database counts/balances.

Tests use cbs_test, explicit restricted runtime/initializer identities, unique IDs and retained synthetic balanced history. No history cleanup, new migration or broad grant was introduced. SQL Server remains the previously authorized unsupported ARM64 emulation experiment. Formal task acceptance and independent learner mastery remain unassessed. Next are the debit/transfer slices and the remaining retry, failure and mixed-operation evidence.
