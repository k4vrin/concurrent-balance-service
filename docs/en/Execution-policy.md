# Execution policy snapshots and credit limits

[فارسی](../fa/Execution-policy.md) · [Amount decisions](Amount-limits.md) · [Coordinator](Operation-coordinator.md) · [Persistence contract](CBS09-contract.md)

CBS11 now has two framework-free application types for the inputs to a future financial attempt. They do not load configuration, access SQL Server, reserve transaction IDs or execute credit. Public mutation routes remain unavailable. The accepted initial deployment remains IRR with both caps at 10000000000000 rials; generic policy types do not hardcode that deployment choice.

## Keep policy inputs together

Version validation belongs to the package-private `IdentityText` utility, shared by execution versions, funding versions and optional actor identities. It requires present, nonblank, well-formed Unicode text without trimming or normalization. `ExecutionPolicy` no longer depends on `CanonicalFingerprint`. Fingerprinting retains its own strict byte encoding and hashing responsibility.

`ExecutionPolicy` contains an execution-policy version, immutable `SupportedCurrencies`, `AmountLimits` and a copied map from currency to `FundingSelection`. Execution-policy and funding-policy versions remain distinct. Every supported currency must have exactly one mapping; extra or missing mappings fail construction as invalid configuration. An empty supported set with an empty map preserves the existing all-currencies-disabled option.

The map is copied at construction and its accessor is immutable. Replacing a loader's map entry cannot change a snapshot already held by an operation. A future coordinator must still retain the same snapshot across attempts and persist its relevant evidence. This value object alone does not implement retry or durable version management.

`fundingFor(currency)` returns the configured server-owned counterpart and its funding-policy version. Unsupported lookup is rejected by the existing supported-currency check. Construction does not establish account existence, matching currency, active status or FUNDING_CLEARING category. Those checks remain inside the future locked transaction; missing internal funding is a configuration defect, not a customer business rejection. Lock the complete deduplicated participant set in shared application order.

## Check limits without changing representation

`AmountLimits` requires positive configured caps and offers two predicates:

| Predicate | Acceptance |
| --- | --- |
| acceptsTransactionAmount(amount) | amount > 0 and amount <= maxTransactionAmount |
| acceptsCustomerBalance(balance) | balance >= 0 and balance <= maxCustomerBalance |

Tests accept transaction amount 1 or exactly 10000000000000 and reject 10000000000001, Long.MAX_VALUE, zero and negative values. Customer balance zero and the exact cap pass; negative and above-cap balances fail. The caps are independent settings even though their initial values match. A transfer checks the moved amount once, not the sum of its postings.

The customer predicate does not apply to funding, which keeps its signed-long category rules. Predicates perform comparisons only. Checked journal projection arithmetic must calculate proposed balances before any monetary mutation. These types do not replace arithmetic, funds, category or status validation.

`Money` and positive command validation retain their representation rules. An above-cap positive request remains parseable and storable for terminal rejection. Neither a false predicate nor an unsupported lookup here stores a rejection or chooses an HTTP status. Existing-outcome lookup and canonical comparison must precede current mutable policy evaluation. Replaying an old rejection after a cap increase must return the original decision. The canonical request continues to describe caller intent rather than current caps.

## Verification and next step

The test-first compile failed because the types did not exist. Final Java 21 `./mvnw -o -B -ntp verify` passed 266 portable tests, zero failures/errors/skips, in 8.523 seconds, including 19 new policy cases. They cover exact boundaries, invalid caps, separate caps, funding-map ownership, changed snapshots, unsupported currency, disabled currencies, invalid version text and missing snapshot parts. These are policy-value tests, not SQL Server financial evidence; the earlier 106-test database run predates these types.

Next add intentional external policy loading and durable credit: recover existing identity before policy evaluation, retain inputs for new execution, reserve identity, validate locked accounts/projections and persist balanced history with the original response. Terminal cap outcome mapping, replay and failure tests remain pending. No task acceptance or learner mastery follows from these assisted changes.
