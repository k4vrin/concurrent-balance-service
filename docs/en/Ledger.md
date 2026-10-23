# V1 ledger design

[فارسی](../fa/Ledger.md) · [Accepted ADR-007](../adr/ADR-007-double-entry-ledger.en.md) · [Constraint review](Database-review.md) · [Debit and credit across account types](Debit-and-credit.md) · [Journal and projection walkthrough](Journal-projection.md)

The user selected option E on 2026-10-22. V1 includes a flexible double-entry ledger in the same SQL Server database as the balance API. This is an accepted project design extension, not a requirement attributed to the original employer challenge. No financial schema, posting engine or endpoint is implemented yet. SQL Server behavior and performance remain untested.

## Four tables and their responsibilities

| Table | Responsibility |
| --- | --- |
| `accounts` | Account identity, category, currency and persisted current balance projection |
| `operations` | Global transaction ID, canonical request fingerprint and stored terminal success or business rejection |
| `journals` | Accounting header linked uniquely to one successful operation |
| `postings` | Immutable debit/credit lines referencing a journal and an account, with positive rial amounts in V1 |

A successful mutation has exactly one journal initially. A journal supports multiple lines, with at least one debit and one credit. Each journal balances per currency. Initial credit, debit and transfer paths use two lines. One SQL Server database and one SQL namespace are sufficient. Separate `ledger` and `posting` SQL schemas are optional organization, not an accounting requirement. No extra `ledger_transactions`, `transfer_requests` or separate ledger service is required for V1. Later intents, outbox and consumer tables keep their own responsibilities.

## Account meaning and examples

V1 uses IRR and checked Java `long` rial amounts, with one unit equal to one rial. SQL Server stores these amounts and balance projections as `BIGINT`; business limits remain under review. Future FX rates and intermediate calculations use `BigDecimal` and an explicit posting-rounding policy; multi-currency ledger design remains CBS38 work. Account categories and sign rules must be encoded in the CBS02–05 review. The initial model uses customer liability accounts with credit-normal balances and a server-selected funding/clearing counterpart with a debit-normal balance. A customer's displayed balance is credits minus debits; the counterpart balance is debits minus credits. Customer balances cannot be negative. A synthetic funding/clearing account may be signed within the long range; its policy must not imply verified external liquidity. Real settlement, overdrafts and fee accounts need separate decisions.

| Operation, amount 100 | Debit posting | Credit posting |
| --- | --- | --- |
| Credit customer A | Funding/clearing 100 | Customer A 100 |
| Debit customer A | Customer A 100 | Funding/clearing 100 |
| Transfer A to B | Customer A 100 | Customer B 100 |
| Initialize A with 100 | Funding/clearing 100 | Customer A 100 |

Debit and credit describe accounting sides, not universally positive and negative numbers. The API's credit/debit names describe the customer's balance change. Callers do not choose arbitrary system accounts. Funding configuration must be stable across retry and represented in the effective operation identity under the reviewed fingerprint policy.

Every account starts with a zero projection. Nonzero initialization posts an internal opening operation through the same accounting transaction path. It is not a fifth public endpoint. Internal IDs share the global uniqueness guarantee and need a reviewed generation policy. Never seed a nonzero balance with an unaudited SQL update. An empty account may exist without a journal; a rejected operation has no journal or postings.

## Transaction and failure boundary

The CBS03–04 protocol must specify identity contention, isolation and whole-transaction retry before migration. Reserve or resolve durable operation identity, lock every participating customer and system account in the same stable order, validate current balances/currencies and calculate checked deltas. Construct and validate the complete balanced journal before persistence. Commit operation success, its journal, all postings and all account projections in one local transaction. Add outbox facts to this same commit only in P6. Reads return committed synchronous projections; they do not wait for Kafka.

A durable business rejection stores only its terminal operation outcome and no monetary effect. A technical failure before commit rolls back all new operation, journal, posting and projection changes. A lost response after commit is recovered with the same transaction ID and original stored result. Replay creates no new journal or postings. If identity contention aborts a transaction, recovery must use a valid new transaction. Ordered account locks reduce account-order deadlocks but do not prove SQL Server cannot deadlock.

A shared funding account can become a hot row even for otherwise independent customer credits/debits. Measure that contention. Test independent progress with disjoint full posting-account sets, for example separate customer transfers. Do not split funding accounts or weaken atomicity without a reviewed decision.

## Constraints, reconciliation and corrections

Review PKs, mandatory references, operation-to-journal uniqueness, account-category policies, positive posting amounts, debit/credit allowlists and currency-consistent references before DDL. An ordinary row CHECK cannot enforce a sum across child postings or guarantee that success has a complete journal. Balanced construction, a single controlled posting path, restricted alternate writers and real SQL Server failure tests must enforce those cross-row invariants.

Reconciliation independently verifies each committed journal's debit/credit totals per currency, exactly one journal for every successful mutation, no journal for rejection, no orphan lines and exact equality of every account projection with its entire posting history, including opening entries. Use a consistent committed snapshot or a quiescent fixture so concurrent commits cannot create a false mismatch. Sum using an exact wider accumulator, such as Java BigInteger or reviewed SQL decimal arithmetic, before checking the long boundary. Balanced totals alone cannot detect posting to the wrong account; compare operation intent and independently expected outcomes too.

Committed journals and postings are append-only. Correct money movement with a new reversing operation/journal and its own identity, retaining the original evidence. A public reversal endpoint is outside the four-operation V1 scope. A projection repair requires a controlled, reviewed maintenance procedure and reconciliation evidence; it never edits historical postings or silently rewrites a stored operation outcome. Do not delete retained operation identity and allow the same command to execute again.

## Delivery and evidence

CBS02–04 review constraints, identity and transactions; CBS05 adds accounting domain rules; CBS09 introduces the reviewed four-table migration; CBS10 adds mappings, opening fixtures and committed balance reads; CBS11–15 add complete HTTP operation slices; CBS16–20 prove replay, rollback, races and independent reconciliation on SQL Server. CBS21–22 measure posting reads, storage, log work and funding contention. Keep one guided substep per session and preserve existing checkpoint CBS00.

Acceptance requires actual SQL Server integration tests with positive test counts, checked arithmetic, mixed operations, simultaneous duplicates across instances, failure after posting/projection writes, restart and lost-response recovery. The portable health smoke test proves none of these. No business migration is authorized before the constraint review. Existing bootstrap code does not require a replacement financial implementation because none exists yet.

## CBS05 posting/journal domain increment, 2026-10-23

Implemented domain types: PostingSide, immutable Posting and immutable Journal. Posting requires an AccountId, side and positive Money amount. balanceDelta(category) maps customer CREDIT to positive and DEBIT to negative; funding DEBIT is positive and CREDIT negative. It does not verify that the supplied category belongs to a resolved account; actual account lookup/currency matching remain in the future projection path. Positive amounts make negation representable even at Long.MAX_VALUE.

Journal defensively copies the posting list, requires at least two non-null lines, enforces one currency and compares exact BigInteger debit/credit totals. Positive amounts plus equal totals require both sides. It supports multi-line journals and balanced totals larger than long, without implying that any resulting account projection fits its range. Mixed-currency journal construction is rejected in this V1 domain increment; future FX needs its separate review.

This implemented example assumes existing typed sourceId/destinationId and Java imports:

```java
var amount = new Money(100, Currency.getInstance("IRR"));
var journal = new Journal(List.of(
        new Posting(sourceId, PostingSide.DEBIT, amount),
        new Posting(destinationId, PostingSide.CREDIT, amount)
));
```

This constructs a valid posting set only. It does not move money, lock accounts, create an operation/header ID or commit a journal. Persisted header identity/operation linkage comes later. Before financial persistence, resolve every account, verify account currencies and compute net deltas with exact wider accumulation per account, then validate all resulting projections before any write. Applying individual lines sequentially can falsely reject a valid net result when an account occurs more than once. Database permissions still enforce append-only persisted history; Java immutability alone does not.

## CBS05 net balance projection, 2026-10-23

`Journal.projectBalances(Map<AccountId, Account>)` now calculates immutable proposed snapshots for the journal's participating accounts. It resolves each posting through the supplied map, rejects missing accounts, mismatched key/identity and account currencies, and uses each resolved account's category for `balanceDelta`. Extra nonparticipating accounts do not appear in the result; participants with zero net change do.

All changes for one account are accumulated exactly with `BigInteger`. Only the final value, current balance plus net change, is converted with `longValueExact` and validated by the Account constructor. A net delta can exceed long even when both old and new signed funding balances fit. No partial result escapes if any participant fails; input snapshots stay unchanged. The returned map is immutable and its iteration order has no locking meaning.

The learner correctly predicted 100 rials from a starting customer balance of 50, debit 100 and credit 150. The balancing third line is funding debit 50. The implementation validates the final 100 instead of rejecting a temporary -50 caused by applying the first line alone. Reordering the same posting set produces the same projections. This domain calculation does not override the API's self-transfer rejection policy.

The caller must eventually supply current snapshots obtained under the accepted financial transaction/locking protocol. This method does not load or lock database rows, authorize a request, persist projections or create a terminal operation outcome. Invalid domain input currently throws IllegalArgumentException, null input throws NullPointerException, and final numeric overflow throws ArithmeticException; HTTP/business-outcome mapping remains future work.

Java 21.0.11 Maven verify passed 92 tests, zero failures/errors/skips in 8.354 seconds: 13 projection cases plus the previous 79. Projection tests cover transfer and category effects, repeated-account netting, reversed line order, temporary overdraft/overflow, net deltas beyond long, final positive/negative overflow, customer nonnegative policy, missing/misidentified accounts, currency, nulls and immutable participant-only results. This is portable domain evidence, not SQL Server atomicity/concurrency evidence. Next CBS05 work is command validation and transaction identity boundaries before HTTP adaptation.

Refactoring, 2026-10-23: projectBalances now reads as snapshot copy, exact net calculation and account projection. Private helpers resolve/validate posting accounts, accumulate changes, construct final snapshots and publish an immutable result. Public behavior, exception messages and invariant comments are preserved. Focused Java 21 ledger tests passed 36 cases with zero failures/errors/skips in 5.588 seconds; the full 92-test suite was not rerun for this refactor.

Pre-publication verification, 2026-10-23: Java 21.0.11 Maven verify passed all 92 tests after the projectBalances refactor, zero failures/errors/skips in 7.267 seconds. This supersedes the earlier focused-only verification limit; SQL Server behavior remains untested.
