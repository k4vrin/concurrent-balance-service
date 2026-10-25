> Current CBS13 update, 2026-10-25: [Durable debit](Debit-execution.md) and opt-in credit are verified with 351 portable and 134 SQL Server tests, zero failures/errors/skips. Transfer remains unavailable until CBS14. Earlier implementation-status statements below retain their historical scope.

> Current CBS11–12 credit update, 2026-10-24: [Durable credit](Credit-execution.md) is now active with explicit policy enablement. It commits balanced history/projections/outcome and returns retained success/rejection bytes; debit/transfer keep their 503 fallbacks. Earlier unavailable/adapter-only statements below are historical.

# Persistence mappings, opening fixtures and balance reads

[فارسی](../fa/Persistence.md) · [Schema/setup](Schema.md) · [Journal projection](Journal-projection.md) · [API](API-v1.md) · [Coordinator and SQL locks](Operation-coordinator.md)

## Implemented behavior

The balance route now reads the authoritative SQL Server account projection. It returns 200 with accountId/balanceRial/currency, 404 for an absent account and temporary unavailability when the database query cannot complete. Credit, debit and transfer remain explicit 503 fallbacks until their durable handlers exist. No schema migration changed; Hibernate validates the existing V1 mappings during normal startup.

| Component | Responsibility |
| --- | --- |
| AccountEntity | Map the account UUID/category/currency/status/balance; only balance participates in ORM updates. Convert locked state into immutable domain snapshots. |
| OperationEntity | Read retained request, policy, funding and outcome metadata, with defensive byte-array access. Immutable ORM mapping. |
| JournalEntity / PostingEntity / PostingId | Read immutable headers/lines with the composite journal/line key. No ORM relationships, cascade operations or history mutation API. |
| AccountStore | Projection-only balance query and ordered native account-lock lookups. |
| BalanceReader / GetBalanceHandler | Application query port and READ_COMMITTED, read-only transaction; missing-account and unavailable mapping. |
| OpeningFixture | Test-only atomic internal opening through the actual JPA transaction manager, shared domain journal calculation and runtime authority. No production bean or public endpoint. |

UUID maps to SQL Server uniqueidentifier. Currency explicitly maps to nchar(3), retained Unicode text to nvarchar, hashes to binary(32), payloads to varbinary(max), and UTC database timestamps to LocalDateTime. The timestamp columns contain UTC by contract; LocalDateTime alone does not carry a timezone. SQL identifiers and application/domain value types remain separate. Actual startup schema validation and committed round trips verify these choices.

### AccountStore query layout

`AccountStore` keeps its JPQL balance query and native locking SQL in named constants. Both queries bind `accountId` by name and use the same two-second query-timeout hint. `BalanceProjection` maps the JPQL `Tuple` by typed aliases before creating `Money`, replacing positional `Object[]` access. `lockAccount` owns the point lookup and refresh, while `requireWritableTransaction` guards the write path. Public JavaDoc describes caller-owned transactions, missing accounts, map ordering and the managed entities returned to the writer.

These are readability changes to the adapter. The existing SQL Server tests cover projection reads, lock lifetime, ordering and stale-state refresh; portable verification alone does not establish that the revised query binding and mapping work against SQL Server.

## Read path

The existing thin controller parses AccountId, calls BalanceOperations.balance and returns BalanceResponse. Composition now delegates that query to GetBalanceHandler; mutation methods keep their unavailable implementation. The query selects only balance and currency from AccountEntity. It does not load a writable Account aggregate, journals, postings or operation responses and never creates operation identity. Query timeout is 2 seconds and the read-only transaction timeout is 5 seconds. The smoke-only portable context still excludes persistence and retains its unavailable composition.

With the local RCSI-disabled configuration, a read encountering an uncommitted projection waits rather than exposing it. The integration test holds a temporary projection change in one transaction, sends an actual HTTP GET from another connection, observes 503 after query timeout, then rolls back and reads zero. This is evidence for the tested configuration, not a universal lock-free-read claim.

## Lock path

AccountStore.lockAll requires an active writable JPA transaction. It deduplicates the complete participant set, sorts with AccountLockOrder.DESCENDING and performs sequential native UPDLOCK/HOLDLOCK point queries. A missing row is omitted from the returned map, while the lookup retains database range protection. The caller still decides whether absence is a terminal requested-account rejection or an internal configuration defect.

A native query may return an entity already present in the persistence context. After acquiring the database lock, the adapter refreshes that managed entity before exposing its snapshot. Integration tests demonstrate stale state becoming current, an external writer blocked until the JPA transaction ends, and the same deduplicated application order. Take all locks before changing managed projections; refresh is not a mechanism for preserving earlier unflushed edits. Whole-attempt policy, reservation/replay and retry/session-setting management remain the coordinator's next-stage responsibility.

## Example opening flow

The test first creates two distinct zero-balance accounts with initializer credentials: customer C and synthetic funding F. It then uses only runtime credentials in a guarded TransactionTemplate to open C with 150000 IRR:

1. Allocate an internal transaction ID, operation UUID and journal UUID. Encode CanonicalRequest.opening and retain FundingSelection plus both fingerprints.
2. Insert a private PENDING operation before account locks. The fixture refuses an ambient transaction and uses the initial IRR/amount cap.
3. Lock C/F in the shared order. Require an active zero customer and an active funding account of the correct categories.
4. Construct funding DEBIT 150000 and customer CREDIT 150000. Journal validates exact balance; projectBalances calculates every validated snapshot before mutation.
5. Insert the header and two postings. Apply both projections through managed AccountEntity instances and flush. Both displayed category-normal balances become 150000.
6. Store the terminal success and internal JSON response; expose the fixture result only after TransactionTemplate confirms commit.
7. Read through a fresh persistence context and the real balance HTTP route. Independently sum committed decimal(38,0) posting totals and compare with the expected projection and balanced journal.

The fixture deliberately injects technical failure after the journal, postings and projection flush. In each case the operation reservation, all history and both projections roll back. Zero accounts created before the attempt remain. This demonstrates the opening fixture's transaction participation, not a completed production credit/replay engine.

Successful fixtures commit synthetic balanced history in cbs_test and retain it on repeated runs. Tests use new UUIDs and count only their own records; they never clean or delete retained history. Previous schema-only evidence showing zero operations is historical. Failed fixtures add no monetary history.

## Verification and next boundary

Java 21.0.11 full `./mvnw -Psqlserver-it verify` passed 240 portable plus 103 actual SQL Server tests, zero failures/errors/skips, in 23.760 seconds. Nine new persistence/HTTP cases took 6.024 seconds. The suite verifies all four entity round trips, schema validation, committed/zero/missing HTTP reads, ambient/read-only lock rejection, freshness, lock lifetime, dirty-read prevention, exact opening totals and three rollback boundaries.

The first implementation run exposed Spring's translation of repository IllegalStateException into InvalidDataAccessApiUsageException; the guard test now verifies that actual outer exception and root cause. Final positive reports supersede the earlier failed development run. Credentials remain outside Git and fixture data is synthetic. The underlying ARM64 SQL Server experiment remains unsupported by Microsoft.

Next is the durable credit slice: coordinator, policy/funding snapshots, reservation/replay/conflict, original response retention and complete HTTP-to-database success. Public mutations, authentication/ownership, whole-attempt retry, uncertain commit and full mixed-operation reconciliation remain pending. Formal learner acceptance is separate from these assistant-run tests.
