# V1 ledger design

[فارسی](../fa/Ledger.md) · [Accepted ADR-007](../adr/ADR-007-double-entry-ledger.en.md) · [Constraint review](Database-review.md)

The user selected option E on 2026-10-22. V1 includes a flexible double-entry ledger in the same SQL Server database as the balance API. This is an accepted project design extension, not a requirement attributed to the original employer challenge. No financial schema, posting engine or endpoint is implemented yet. SQL Server behavior and performance remain untested.

## Four tables and their responsibilities

| Table | Responsibility |
| --- | --- |
| `accounts` | Account identity, category, currency and persisted current balance projection |
| `operations` | Global transaction ID, canonical request fingerprint and stored terminal success or business rejection |
| `journals` | Accounting header linked uniquely to one successful operation |
| `postings` | Immutable debit/credit lines referencing a journal and an account, with positive integer minor-unit amounts |

A successful mutation has exactly one journal initially. A journal supports multiple lines, with at least one debit and one credit. Each journal balances per currency. Initial credit, debit and transfer paths use two lines. One SQL Server database and one SQL namespace are sufficient. Separate `ledger` and `posting` SQL schemas are optional organization, not an accounting requirement. No extra `ledger_transactions`, `transfer_requests` or separate ledger service is required for V1. Later intents, outbox and consumer tables keep their own responsibilities.

## Account meaning and examples

V1 uses one explicitly selected currency and checked Java `long` minor units. Account categories and sign rules must be encoded in the CBS02–05 review. The initial model uses customer liability accounts with credit-normal balances and a server-selected funding/clearing counterpart with a debit-normal balance. A customer's displayed balance is credits minus debits; the counterpart balance is debits minus credits. Customer balances cannot be negative. A synthetic funding/clearing account may be signed within the long range; its policy must not imply verified external liquidity. Real settlement, overdrafts and fee accounts need separate decisions.

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
