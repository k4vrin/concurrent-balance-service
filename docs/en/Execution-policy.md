# Execution policy snapshots and credit limits

[فارسی](../fa/Execution-policy.md) · [Amount decisions](Amount-limits.md) · [Coordinator](Operation-coordinator.md) · [Persistence contract](CBS09-contract.md)

CBS11 has two framework-free application types for financial-attempt inputs and an optional typed Spring configuration adapter. The value types do not access SQL Server, reserve transaction IDs or execute credit. The [durable credit slice](Credit-execution.md) now consumes these inputs when enabled; debit and transfer remain unavailable. The accepted initial deployment remains IRR with both caps at 10000000000000 rials; generic policy types do not hardcode that deployment choice.

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

The next section describes the subsequently implemented external loader. The subsequent [credit implementation](Credit-execution.md) supplies identity lookup, retained inputs, reservation, locked checks, atomic persistence, terminal cap outcomes and replay. Broader retry and mixed-operation gates remain pending. No task acceptance or learner mastery follows from these assisted changes.

## Load one snapshot from Spring configuration

`ExecutionPolicyProperties` binds `balance.execution` into typed currencies, UUIDs, caps and version strings. `ExecutionPolicyConfiguration` creates one validated `ExecutionPolicy` bean at startup when `balance.execution.enabled=true`. Activation defaults to false; enabling it now also wires the durable credit handler. It does not supply authentication or create accounts.

| Property | Default / requirement |
| --- | --- |
| version | execution-v1 |
| supported-currencies | IRR |
| max-transaction-amount | 10000000000000 |
| max-customer-balance | 10000000000000 |
| funding[currency].account-id | Required UUID for each supported currency |
| funding[currency].policy-version | Required nonblank valid-Unicode version |

The built-in application YAML supplies an IRR mapping through environment placeholders. Export these alongside normal runtime database settings to load it:

```bash
export EXECUTION_POLICY_ENABLED=true
export EXECUTION_IRR_FUNDING_ACCOUNT_ID='<UUID of the selected internal funding account>'
export EXECUTION_IRR_FUNDING_POLICY_VERSION='funding-v1'
```

The angle-bracket value is a placeholder, not a usable UUID. No account ID is invented or seeded by the loader. `.env.example` is a reference and is not loaded by Spring automatically. Optional scalar environment overrides include `BALANCE_EXECUTION_VERSION`, `BALANCE_EXECUTION_MAXTRANSACTIONAMOUNT` and `BALANCE_EXECUTION_MAXCUSTOMERBALANCE`. The typed binder supports supplied currency sets/maps; replacing the built-in IRR deployment with a different set requires a complete matching configuration, not just changing a currency variable. This does not introduce FX behavior.

When enabled, malformed UUID/currency values, invalid caps, invalid versions and incomplete mappings fail startup. Disabled configuration creates no policy bean. The standalone smoke test assembly does not import the policy configuration; production policy activation has no smoke-profile condition. The bean is a startup snapshot without hot reload or a database policy store. Change policy versions intentionally when deploying changed rules; the loader does not generate versions or enforce their global uniqueness. Existing durable outcomes must still replay before current policy evaluation. Account existence, category, status and actual currency remain locked-handler checks.

Fourteen new portable context/binding cases include actual YAML-placeholder resolution and missing-value failure. After this adapter was added, Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed 291 portable plus 106 SQL Server tests, zero failures/errors/skips, in 21.673 seconds. The SQL regression run uses the default disabled policy switch; enabled binding is verified by the portable contexts. These results do not establish a working credit operation. The ARM64 database environment remains unsupported emulation.
