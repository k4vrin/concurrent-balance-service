# CBS20 core completion and reproducible acceptance

[فارسی](../fa/Core-acceptance.md) · [Schema setup](Schema.md) · [Docker](Docker.md) · [Recovery evidence](Recovery-acceptance.md)

## Completion boundary

The CBS20/G1 target is the trusted, loopback-only four-operation core: credit, debit, atomic transfer and authoritative balance lookup, with durable request identity, terminal rejection, balanced immutable history and synchronous projections. The selected core implementation and automated engineering acceptance are complete.

Engineering implementation and automated acceptance are distinct from independent learner mastery, employer approval or production readiness. No external/shared deployment is authorized by this milestone. Authentication/ownership, performance experiments, OTP, Redis, Kafka/outbox, CDC and FX remain explicitly deferred extensions after the selected core gate.

## Reproduce from setup

Use Java 21, Python 3 and Docker. The SQL Server image remains pinned in Compose. Review and explicitly accept the Developer license before first startup; ARM64 emulation is the existing unsupported local experiment. No database engine substitution proves these semantics.

1. Copy `.env.example` to the ignored `.env.sqlserver` and set distinct administrator, runtime, migrator and initializer secrets. Keep the file mode 0600 on Unix.
2. Start/bootstrap the existing environment with `docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver` and `docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh`.
3. Follow [Schema.md](Schema.md) to provision restricted identities, migrate/validate both `cbs_dev` and `cbs_test`, and apply the reviewed grants. Runtime never auto-migrates the business schema.
4. Run the single core-verification entry point below. The optional env file is parsed as data, never sourced as shell code.

```bash
python3 scripts/verify-core.py --env-file .env.sqlserver
```

Without `--env-file`, explicitly export all three `SQLSERVER_TEST_*`, `SQLSERVER_MIGRATION_TEST_*` and `SQLSERVER_INITIALIZER_TEST_*` URL/username/password triplets. Actual JDBC connections must report `cbs_test`, Microsoft SQL Server and restricted identities before fixtures are inserted. The script does not use production/runtime settings as fallback. `--offline` selects a populated Maven cache; `--skip-build` runs only the artifact scenario after a separately verified build and records that fact in the report. The automation is validated on this macOS host and on the Linux GitHub runner. Windows users retain the documented Maven/PowerShell test path until this separate harness is measured there.

## What the entry point proves

The default command runs Java 21 Maven `-Psqlserver-it verify`, then extracts only the JDBC driver from the built JAR to compile an independent JDBC checker. It creates three fresh zero-balance accounts through the restricted initializer. All money enters through the packaged application's actual HTTP credit route, never a direct balance seed.

The actual executable JAR starts in a separate JVM with runtime credentials only, an ephemeral loopback port and explicit funding policy. The scenario performs:

- Credits of 1000 and 500 rials, debit of 100 and transfer of 250, yielding customer balances 650/750.
- A 651-rial insufficient-funds transfer, then a 100-rial top-up; the original rejection must replay despite newly sufficient funds.
- Original success replay, conflicting amount under the same ID and fractional input rejection without a retained operation.
- An actual externally held SQL account lock. The blocked transfer returns 503; independent JDBC proves no retained operation. After lock release, same-ID retry transfers 25 once.
- Full application-process shutdown/restart. Success, rejection and recovered-transfer responses must replay byte-for-byte and leave balances unchanged.

The independent JDBC checker requires exactly seven terminal operations: six successes, one rejection, no PENDING, six balanced journals and twelve postings. It reconstructs all three projections from immutable posting history with `decimal(38,0)`. Final customer balances are **725/775** and the funding projection is **1500**. Fixtures are uniquely scoped and retained; the harness never cleans shared history, rotates credentials, changes grants or recreates the database.

Generated evidence lives in ignored `target/core-acceptance/`: Maven log, first/restarted process logs and a JSON summary containing the source HEAD, dirty-tree flag, artifact checksum and measured outcomes. Exact credential scans run before success. Restarted application environments exclude migration/initializer/administrator credentials and ambient Spring/datasource overrides.

## Acceptance matrix

| Gate | Evidence |
| --- | --- |
| Four actual HTTP-to-SQL operations | Packaged-process mixed scenario and integration suites. |
| Balanced history and atomic projections/outcomes | Schema/permission tests, all operation write-boundary rollback cases and independent reconstruction. |
| Durable success/rejection and conflict | CBS17 cross-context races and packaged-process restart/replay. |
| Concurrency and independent progress | CBS16 distinct commands and CBS18 opposite/progress cases. |
| Transient and uncertain failure recovery | Real 1205/1222 cases, CBS19 controlled boundary faults and actual blocked-process rollback/retry. |
| Reproducible bootstrap/verification | Existing pinned Docker/schema runbook plus the entry point above. |
| Complete type Javadoc and source structure | Compiler DocTrees coverage, whole-project doclint, zero nested/local/anonymous source types. |
| CI | Portable verification plus the new disposable SQL Server/package acceptance job; [Linux run 40886355156](https://github.com/k4vrin/concurrent-balance-service/actions/runs/40886355156) passed both jobs at `7b4d505`, including clean bootstrap and the packaged-process scenario. |
| Independent learner acceptance | Requires the learner's own demonstration; not inferred from assisted implementation. |

## Measured result and remaining limits

The final compiler audit covers **163 Java types with zero missing class-level Javadocs**. Whole-project private Javadoc generation passed doclint without warnings/errors; the source scan found **zero nested/local/anonymous types in 161 source files**.

Full Java 21 Maven `-Psqlserver-it verify` passed **421 portable + 212 real SQL Server tests**, with **zero failures/errors/skips**, in **01:05 min** on 2026-10-29. Fresh Surefire/Failsafe XML totals were checked.

The default acceptance entry point then passed the separate packaged-JAR scenario: **7 terminal operations, 6 successes, 1 rejection, 6 journals and 12 postings**; customer balances **725/775**, funding **1500**. Actual process restart, real lock-timeout rollback and same-ID retry passed. The measured artifact SHA-256 was `54c6816d6da294ef477e4a8bfa95c77290e2f95686e20312f65c95870686d730`. The report records the parent source HEAD and a dirty tree because this acceptance preceded publication of the final test/documentation changes.

No timing above is a production SLO or throughput benchmark. Cross-context race fixtures share a JVM; CBS19 acknowledgement/visibility/response faults are explicitly injected rather than physical network partitions. The independent artifact scenario uses an actual separate application JVM, real lock timeout and process restart. These evidence boundaries remain part of the completed core deliverable.

Linux CI independently passed at clean source commit `7b4d505` (`buildSkipped=false`, `workingTreeDirty=false`): [run 40886355156](https://github.com/k4vrin/concurrent-balance-service/actions/runs/40886355156). Its artifact scenario produced the same operation/history counts and balances, verified real rollback and restart, and retained a sanitized summary. CI artifact SHA-256: `2713e30c6080ef383a1ad5891d66f4e4c66f90c094f19e7290d0eb1c31f9327a`.
