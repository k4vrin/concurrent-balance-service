# Implementation status

The public README describes the first release contract. The current checkout has portable domain/HTTP foundations, the verified four-table schema, JPA mappings and a real SQL Server balance route. GET balance now returns 200/404, with temporary query unavailability mapped to 503. Public credit/debit/transfer remain unavailable.

Latest full verification: 240 portable plus 103 real SQL Server tests, zero failures/errors/skips. [Persistence/opening evidence](Persistence.md) covers committed journal-backed test initialization, native JPA lock lifetime, three rollback boundaries, reconciliation and HTTP reads. [Schema/runbook](Schema.md) owns setup.

Next is the durable credit/coordinator/replay slice. Opening initialization is test-only; it is not a fifth public route. The runtime has no finished public money-changing handler, production authorization or full retry/recovery engine.

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
