# V1 amount limits and replay

Implementation update, 2026-10-24: [Execution-policy snapshots](Execution-policy.md) now provide portable inclusive cap predicates and immutable funding selections. Typed opt-in startup loading is also available; durable evaluation/rejection and HTTP integration remain pending.

[فارسی](../fa/Amount-limits.md) · [Requirements](Requirements.md) · [Transaction protocol](Transaction-protocol.md)

## Accepted policy, 2026-10-23

The user supplied `10000000000000` for the requested transaction and customer-balance limits and selected retention of the original cap rejection. The shared initial V1 limit is **10,000,000,000,000 rials** for each credit, debit and transfer, and for each customer balance. Equality is allowed. This records the business decision; runtime policy loading, cap evaluation, durable rejection and schema enforcement remain unimplemented.

| Quantity | Initial V1 rule |
| --- | --- |
| Credit/debit/transfer amount | Positive integer rials, at most 10000000000000 |
| Customer balance after a successful operation | Between 0 and 10000000000000 inclusive |
| Synthetic funding/clearing balance | Existing signed-long category policy; the customer cap does not apply |
| Opening customer balance | Must satisfy the customer balance cap and use the accepted opening journal protocol |

The transfer limit applies to the amount moved once. A transfer of 100 rials has a debit posting of 100 and a credit posting of 100; adding the two lines does not turn it into a 200-rial transfer. The cap does not authorize FX or change the initial IRR deployment policy.

## Representation versus business policy

Java `long` and SQL Server `BIGINT` can represent larger positive amounts than this cap. `Money` deliberately supports signed values for accounting, while mutation commands require positive amounts. The HTTP parser separately rejects fractional numbers and numbers outside signed-long range. Keep the selected cap out of the syntax parser and generic Money constructor: a positive representable request above the business cap must remain recordable for durable rejection.

The future operations table must therefore be able to retain an attempted amount above 10000000000000. A universal `operations.amount <= cap` CHECK would prevent recording that rejection. Customer nonnegativity and numeric representation are structural invariants; enforcement of a configurable business cap also needs an explicit policy snapshot and a controlled write path. The exact SQL constraints remain subject to pre-migration review.

## Worked flow

Assume trusted local access, supported IRR, existing active accounts and no other failing rules. These are design examples, not executed financial operations.

1. `credit-limit-1` requests 10000000000001 rials. It passes integer/positive parsing. The coordinator checks for an existing outcome before current policy evaluation. For a new ID, the accepted protocol must reserve identity and commit a terminal cap rejection with the original request and response, without a journal or balance changes.
2. An identical retry arrives after a hypothetical increase of the cap to 20000000000000. It replays the original rejection. It does not become a successful credit. The larger cap is illustrative and is not an accepted configuration change.
3. Reusing `credit-limit-1` with 100 rials changes the canonical request and produces an identity conflict. A genuinely new business attempt uses a new transaction ID.
4. Customer A has 9999999999950 rials. A new credit of 100 is below the transaction cap but would produce 10000000000050. Under account locks, reject it for exceeding the customer balance cap, with no journal and no projection updates. A credit of 50 reaches exactly the cap and satisfies these two limits.
5. A debit of 100 from a customer with 50 still fails insufficient funds even though it is below the transaction cap. Passing one policy does not bypass the others.

A malformed amount outside signed-long range never becomes a typed command and does not reserve identity. A database connection failure is a technical failure, not a terminal cap decision. HTTP status/reason extensions for the new cap outcomes still need implementation and contract tests; the current fallback remains 503.

## Transaction and policy boundaries

For an existing ID, verify canonical intent and current access, then replay the retained terminal result before reevaluating mutable limits. For a new ID, use one consistent policy snapshot. Check amount limits and projected customer balances within the reviewed reservation/transaction protocol. Resulting balance checks require locked current balances. Prepare every projection before applying any monetary write, and commit success with its balanced journal or rejection without one.

The caller's canonical request contains the amount and currency. The current cap is server policy, so changing it must not change that canonical request or invalidate replay. Retain policy evidence with the original decision; the storage format and versioning remain part of schema review. A loader failure must not silently become a zero cap or a customer rejection.

Lowering a future balance cap below existing balances requires an explicit transition policy. This decision does not select grandfathering, account freezing or automatic balance reduction. No policy administration endpoint or hot-reload mechanism is implemented. See [the concrete SQL review findings](Persistence-review.md) for the remaining migration gates.
