# Implementation status

## CBS16 distinct-operation concurrency, 2026-10-29

Added five real HTTP-to-SQL Server cases for concurrent distinct IDs: credits to one customer or separate customers sharing funding, competing debits, competing transfers and mixed mutations. Bounded client/lock-entry barriers preserve real adapter locking; every worker outcome is collected. Independent SQL verifies exact retained outcomes, zero committed PENDING, one balanced two-posting journal per success, no history on rejection and projection reconstruction before/after concurrent replay. Insufficient-funds losers replay unchanged after top-up. Production code and schema are unchanged. [Scenarios and evidence limits](Concurrency-execution.md).

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 162 real SQL Server tests**, with **zero failures/errors/skips**, in **45.449 seconds**. Fresh Surefire/Failsafe XML totals were checked.

This checkpoint includes the first CBS16 distinct-operation concurrency slice. CBS15 is published at `6f6cc61`. Cross-instance duplicate/conflict races, opposite transfers, independent progress and further response/commit faults remain CBS17–19; learner acceptance stays separate.


## CBS15 bounded whole-transaction recovery, 2026-10-29

Credit, debit and transfer now share `executeRecovering`: fixed identities/policy/funding, confirmed-rollback vendor classification, fresh same-ID recovery and bounded jitter outside transactions. Unknown completion and lost commit acknowledgement allow recovery reads only, never blind reexecution after empty reads. Retry configuration is validated; pool initialization sets the selected lock timeout and driver login/socket limits. See [flow, settings, evidence and limitations](Retry-execution.md).

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 157 real SQL Server tests**, with **zero failures/errors/skips**, in **43.636 seconds**. Fresh Surefire/Failsafe XML totals were checked.

The three new real cases cover transfer rollback/retry on actual 1222 with reconciliation, an actual 1205 victim in an isolated JDBC fixture, and simulated acknowledgement loss after a real committed transfer. Broad multi-instance duplicate/opposite/mixed races and additional response/commit faults remain CBS16–19. Formal learner acceptance remains separate. This checkpoint includes the CBS15 implementation and class-level Javadoc for every created or edited Java type. Earlier checkpoints below retain their original evidence scope.


## Direct controller-to-handler wiring, 2026-10-26

Removed `BalanceOperations`, `HandlerBalanceOperations` and the `stagedOperations` factory. `BalanceController` now constructor-injects the four use-case handlers through Optional parameters and calls their `handle` methods directly. Optionality represents intentionally absent beans: disabled execution keeps balance lookup and returns 503 for mutations; the infrastructure-free smoke assembly returns 503 for all unwired routes. `HttpAdapterConfiguration` only configures strict JSON parsing. There is no ObjectProvider or replacement forwarding service. Transaction ownership remains in the handlers/coordinator, and balance transaction failures retain their unavailable HTTP mapping.

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **388 portable + 154 real SQL Server tests**, zero failures/errors/skips, **37.050 seconds**. Three new portable checks cover balance lookup with mutation handlers absent, all disabled mutation routes, and balance transaction failure mapping. Controller tests mock the actual handlers. The focused web/response/smoke run passed 60 cases; the compiler syntax-tree scan still finds zero nested/local/anonymous types. HTTP-to-database credit, debit, transfer and balance regressions passed. CBS15 and learner acceptance remain open. This publication checkpoint includes the complete transfer, top-level-type and direct-handler changes.


## Top-level implementation types and regression, 2026-10-26

Reviewed every production/test Java source and removed all 23 member/local/anonymous type declarations found by the Java compiler syntax-tree scan. `LockedAccount` is a shared application snapshot; `BalanceProjection`, `StoredOperation` and `OperationCompletion` are top-level package-private persistence types. `OperationSuccess`/`OperationRejected`, `OperationFailureReason` and `FundingProperties` have their own files. `HandlerBalanceOperations` replaces anonymous HTTP dispatch; test opening/account records, datasource and the shared recording transaction manager are also top-level. The repository instructions now prohibit nested/local/anonymous types, including test helpers.

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **385 portable + 154 real SQL Server tests**, zero failures/errors/skips, **39.067 seconds**; fresh XML totals and the zero-result syntax-tree scan were checked. SQL transaction ownership, locks, reservation/write order, schema, configuration keys and HTTP response contracts are preserved. This supersedes the earlier CBS14 run as the current refactored-tree regression evidence. CBS15 and learner acceptance remain open.

The supplied startup log had an unresolved `${DB_URL}` datasource URL. The process needs `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`; the ignored local env file is not automatically loaded by Spring Boot or inherited from a Compose invocation. The [setup troubleshooting section](Docker.md#missing-database-environment) explains the fix. No fallback database, credentials or forced Hibernate dialect were added. Changes remain local and uncommitted.


## CBS14 atomic transfer and full SQL Server regression, 2026-10-26

[Transfer execution and evidence](Transfer-execution.md) records the new handler/store/encoder slice and existing HTTP route wiring. Both customer accounts lock in the shared application UUID order. Success commits one balanced journal, two postings, both projections and the original response together; transfers have no funding leg or funding metadata. Matching retries replay original bytes before current policy. Missing accounts, insufficient funds and cap violations retain their decisions without history. Self-transfer fails before reservation.

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **385 portable plus 154 real SQL Server tests**, zero failures/errors/skips, **36.105 seconds**. The 34 new portable cases cover handler decisions/lifecycle and response validation/encoding; 20 new real cases cover HTTP success/replay, conservation/reconciliation, both missing accounts, exact cap, recipient overflow guard, conflicts, context/pool recreation, lock timeout and rollback after history, source-only balance, both balances and outcome writes with same-ID retry. The overflow guard uses an injected domain snapshot, not committed near-BIGINT history. Startup/pre-login failure and unchanged-container restart recovery are recorded in the guide.

All four HTTP paths are now implemented, but general retry/backoff, uncertain-commit recovery and broad mixed/concurrent-operation gates remain open. Next engineering task is CBS15; formal learner acceptance remains unchanged. Work is local and uncommitted. Earlier sections retain their historical scope.


## CBS13 durable debit and fresh SQL Server regression, 2026-10-25

[Debit execution and evidence](Debit-execution.md) records the explicit debit handler/store/encoder slice and HTTP wiring. Exact funds succeed; insufficient funds commits its original rejection without history, even when a later credit makes funds sufficient. Replay precedes current policy, and global-ID payload/account/type conflicts add no effects. All successful history/projections/retained response share the coordinator's transaction. Transfer remains unavailable and CBS14 is deliberately excluded.

The full Java 21 offline Maven SQL Server profile passed 351 portable and 134 real SQL Server tests, zero failures/errors/skips, in 31.235 seconds. New coverage is 26 portable cases and 14 debit database cases, including real blocked-account timeout, post-write failure rollback, same-ID retry, independent reconciliation and pool/context recreation. Existing credit and AccountStore tuple/named-binding regressions passed. Earlier startup/pre-login failures are recorded in the debit guide; restarting the existing container restored health without image, volume, settings or permission changes. No formal learner acceptance is inferred. General retry/uncertain-commit, auth and mixed-operation gates remain open.


## Journal documentation and debit preparation, 2026-10-25

Added Javadoc for `Journal`, its constructor and every projection helper. New portable journal cases check debit projections for 100 and exact-funds 500 rials, rejection of a negative final customer balance and unchanged input snapshots. Added `DebitAttempt` as an execution-context record and reformatted `CreditAttempt`; no debit handler, persistence adapter or HTTP execution is introduced.

Java 21 wrapper verify with the documented workspace-local socket option passed 325 portable tests, zero failures/errors/skips, in 42.065 seconds. SQL Server integration testing is explicitly deferred to a Docker-capable machine; this checkpoint establishes domain calculations, not durable debit or database atomicity.

## Credit readability and persistence mapping, 2026-10-25

Refactored `CreditHandler` into explicit replay, preparation, execution/recovery and journal/projection helpers. `JpaCreditStore` now uses SQL text blocks, named parameters, a private typed replay row mapped by column labels and an exhaustive completion mapping. Added Javadoc across the credit path and updated both [class walkthroughs](Credit-class-walkthrough.md). Schema, lock ordering, transaction boundaries and write/flush order are preserved by the refactor.

Java 21 wrapper verify passed 322 portable tests with zero failures/errors/skips in 29.746 seconds using the documented workspace-local Unix-domain socket option; plain verify had three smoke errors from that known Windows JDK issue. The targeted `SqlServerCreditIT` attempt failed in fixture setup because `SQLSERVER_INITIALIZER_TEST_URL` was missing. No real SQL Server regression case passed for this refactor; native mapping/binding verification is the next gate before debit and transfer work. Earlier database results below remain historical evidence.

## Portable credit-handler coverage, 2026-10-25

Added 16 database-free `CreditHandlerTests` cases using the real handler/coordinator and domain calculations, mocked storage/encoding interfaces and a recording Spring transaction manager. Coverage includes success at the balance cap, retained success/rejection before disabled-currency policy, replay conflict/corruption, new unsupported currency, three terminal rejections, funding overflow, three storage failure stages, commit failure and duplicate-reservation recovery with/without a visible result. Production code and the SQL Server suite are unchanged. The [workflow](Workflow.md) documents the separate portable and integration loops.

On Windows with Java 21.0.12.1, the final command passed 322 tests with zero failures/errors/skips in 25.489 seconds:

```powershell
.\mvnw.cmd -o -B -ntp "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/Projects/concurrent-balance-service/target" verify
```

The first sandbox attempt could not write Maven cache locks. The first executable suite exposed two incorrect new test expectations about debit-normal funding signs; these were corrected. The subsequent run passed the 16 new tests but had three existing smoke-context errors from the JDK's local Unix-domain socket connection. Selecting IPv4 did not resolve that error; a workspace-local socket directory did, first in the three smoke tests and then in full verification. No tests were excluded. SQL Server integration tests were not run for this test/documentation change. This evidence verifies application decisions and Spring lifecycle handling, not database locks, durability or rollback; the prior SQL Server limitations below remain open.

## Smoke assembly and current verification, 2026-10-25

Removed all production `@Profile("!smoke")` exclusions. A standalone `HttpSmokeConfiguration` in test sources imports only the HTTP boundary; the test checks that financial/persistence beans are absent even with execution enabled. Production coordinator and policy tests now verify normal dependency/property behavior independently of the test profile. The [class walkthrough](Credit-class-walkthrough.md) explains all participating classes, funding, identity and success/failure/replay flows.

The corrected assembly passed all 306 portable tests with zero failures/errors/skips. The attempted SQL Server phase did not complete: first the explicit test environment was missing, then the configured test connection was refused. The existing stopped container was started without image/volume/settings changes, but exited with status 114 during startup. No current SQL Server pass is claimed; the earlier 305+120 result below predates this smoke refactor. The initial standalone assembly attempt failed because `@TestConfiguration` added the main application; it was corrected to a standalone test `@Configuration`. Earlier sandbox runs also encountered Mockito attachment restrictions. At the user's request, further testing stopped. No database runtime guarantee or completed retry/concurrency gate is inferred from the portable pass.

The public README describes the first release contract. The current checkout has the four-table schema, JPA mappings, committed SQL Server balance reads and opt-in durable credit. Set balance.execution.enabled=true with valid funding configuration to activate credit; debit and transfer remain unavailable. The trusted local access scope remains unchanged.

Earlier full verification before the smoke refactor: 305 portable plus 120 real SQL Server tests, zero failures/errors/skips, 26.840 seconds. [Credit execution and replay evidence](Credit-execution.md) covers atomic history/projections/outcome, exact response replay, retained rejection, context/pool recreation, one competing-reservation race and injected rollback. [Persistence/opening evidence](Persistence.md) and [schema/runbook](Schema.md) remain linked for setup.

Next are debit/transfer slices and remaining failure/retry/mixed-operation gates. General transient retry/backoff, uncertain-commit recovery, authentication and ownership are not implemented. Opening remains test-only. Earlier checkpoint sections below retain their historical scope and are superseded by the current summary where implementation advanced.

## Coordinator foundation, 2026-10-24

[Design walkthrough and SQL Server lock examples](Operation-coordinator.md) explain transaction ownership, manager injection, isolation alternatives and UPDLOCK/HOLDLOCK.

`OperationCoordinator` now owns a writable `READ_COMMITTED` transaction through a constructor-injected `PlatformTransactionManager` and a private `TransactionTemplate`. It rejects an ambient transaction, lets technical failures escape and returns the callback result only after the template completes successfully. It is a plain class pending credit-handler wiring; it does not yet reserve identities, persist outcomes or retry failures.

Five portable tests exercise Spring's transaction lifecycle with a recording transaction manager: isolation/writability and return after commit, rollback on an attempt exception, propagation of commit failure, and rejection of writable/read-only ambient transactions before callback execution. These are lifecycle tests, not SQL Server commit/rollback evidence. The test-first compile failed because the coordinator was absent; after implementation, `./mvnw -o -B -ntp verify` passed 245 tests with zero failures, errors or skips on Java 21. SQL Server integration tests were not rerun for this increment; the earlier 103-test result above remains historical evidence.

## Spring wiring checkpoint, 2026-10-24

`OperationCoordinator` is now a `@Service` with `@Profile("!smoke")`, matching the persistence components. Spring supplies its constructor's transaction manager in the normal application context; the test-only smoke context excludes the coordinator because it intentionally has no persistence infrastructure. Credit-handler wiring is still pending. Two new portable context tests verify manager injection and smoke exclusion. Full Java 21 `./mvnw -o -B -ntp verify` passed 247 tests, zero failures/errors/skips, in 8.219 seconds.

Three new `SqlServerPersistenceIT` cases are written for JPA participation/READ_COMMITTED and committed probe visibility, rollback visible from a fresh JDBC connection, and ambient JPA transaction rejection. They use an existing uniquely named temporary-table fixture and do not write financial history. They remain unverified: the initial database attempt failed at setup with connection refused; restarting the existing container failed with SQL Server page-allocation errors 17300/17312. The full profile attempt also failed at database setup with 17 errors, not passing database tests. The container reported exit 235; no volume was deleted, schema changed or Docker resource setting changed. Historical 103-test database evidence above does not verify this increment. Restore the local SQL Server environment and rerun `./mvnw -Psqlserver-it verify` before claiming runtime JPA acceptance.

## Successful database retest, 2026-10-24

The coordinator/JPA checks above are now verified. SQL Server was healthy internally, but Docker's active published-port map was empty despite the retained 127.0.0.1:14333 configuration. Restarting the existing container restored that mapping without changing the image, volume, schema or resource settings. This connectivity fault is distinct from the earlier startup memory failure; its resolution does not establish that failure's cause.

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed 247 portable plus 106 SQL Server tests, zero failures/errors/skips, in 27.706 seconds. Fresh XML totals were checked. All three new coordinator cases passed: the injected manager is JpaTransactionManager, the EntityManager joins a READ_COMMITTED transaction, a committed probe is visible from another connection, a failed probe rolls back, and an ambient JPA transaction is rejected before work. Temporary probes clean up their own generated tables; normal opening tests retain their synthetic committed ledger history. No configured passwords appeared in the final log/reports. This supersedes the unverified disposition above, not the recorded failure history. Credit execution/replay/recovery and formal learner acceptance remain pending; ARM64 emulation remains unsupported.

## CBS11 policy inputs, 2026-10-24

After publishing coordinator commit `386fe8c`, added framework-free `AmountLimits` and `ExecutionPolicy`: inclusive independent transaction/customer caps, immutable supported-currency funding mappings and explicit execution/funding versions. Missing/extra counterparts fail snapshot construction; account existence/status/category/currency validation remains a locked-handler responsibility. See [execution-policy guide](Execution-policy.md). No configuration loader, durable cap rejection, credit write path or replay is added.

Test-first compilation failed before the types existed. Java 21 `./mvnw -o -B -ntp verify` passed 266 portable tests, zero failures/errors/skips, in 8.523 seconds, including 19 new cases. SQL Server tests were not rerun for these unused application value types; the prior 106-test run verifies the coordinator checkpoint. Next: external policy loading and durable credit reservation, accounting and outcome/replay.

## Identity validation separation, 2026-10-24

At review request, removed ExecutionPolicy's dependency on CanonicalFingerprint. The recurring present/nonblank/valid-Unicode checks now live in package-private IdentityText, shared by execution versions, funding versions and optional actor bindings. Fingerprint encoding remains strict and its byte/hash contract is unchanged. Eleven focused identity cases cover valid Unicode including surrogate pairs, malformed surrogates, blanks and null. The test-first compile failed before the utility existed; full Java 21 portable verify then passed 277 tests with zero failures/errors/skips in 7.184 seconds, including existing canonical-vector tests. No SQL Server run or new runtime financial behavior is claimed.

## CBS11 typed policy loading, 2026-10-24

After publishing policy/identity checkpoint `a13a54c`, added ExecutionPolicyProperties and ExecutionPolicyConfiguration. Outside smoke and only when explicitly enabled, typed configuration produces one validated immutable ExecutionPolicy at startup. Initial defaults are execution-v1, IRR and both accepted 10000000000000 caps; funding UUID/version must be supplied. Application YAML and .env.example document the opt-in IRR environment inputs. Invalid enabled settings fail startup; no account is invented or validated against the database by this loader. See [policy configuration](Execution-policy.md).

Fourteen new context/binding tests cover defaults, overrides, invalid/missing settings, smoke exclusion and actual YAML placeholders. Test-first compilation failed before the adapter existed. Final Java 21 ./mvnw -o -B -ntp -Psqlserver-it verify passed 291 portable plus 106 SQL Server tests, zero failures/errors/skips, in 21.673 seconds. SQL regression uses the disabled policy default; enabled binding is portable context evidence. No public credit execution, reservation, stored outcomes/replay or hot reload exists yet. Next is the durable credit transaction and retained response.

## Durable credit and initial replay/conflict, 2026-10-24

After publishing configuration commit 1c51748, implemented CreditHandler/CreditStore/JpaCreditStore and original response retention. Explicit policy enablement now activates public credit. One owned transaction reserves identity before sorted account locks, validates complete checked projections and commits journal/postings, both balances and the terminal response. Replay compares full canonical intent before current policy; a named identity collision leaves its failed transaction before fresh recovery. Cap and missing-account rejections commit without monetary writes. HTTP returns retained success/problem bytes through the extended sealed response family. Debit/transfer stay unavailable.

Final Java 21 ./mvnw -o -B -ntp -Psqlserver-it verify passed 305 portable plus 120 SQL Server tests, zero failures/errors/skips, 26.840 seconds. Fourteen new real credit cases include HTTP replay/conflict, cap/missing-account retention, context/pool recreation, one named-uniqueness race, exact accounting counts/totals and rollback boundaries. The funding-overflow case injects a domain snapshot into a real reservation transaction; it is not SQL overflow evidence. Initial three fault assertions were corrected for Spring repository translation and now retain/verify the original cause. General transient/uncertain-commit recovery and full mixed-operation gates remain pending. See Credit-execution.md. New work remains local after the requested checkpoint push; no learner acceptance changed.
