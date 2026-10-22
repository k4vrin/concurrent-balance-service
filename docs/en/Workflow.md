> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Git and bilingual documentation workflow

Status: workflow for this repository. Branch protection and milestone tags are planned; their configuration is not established by this document. [فارسی](../fa/Workflow.md)

## Branch and release model

Target a protected, always-buildable `main` with short feature branches and pull requests. Enable protection after the initial CI check exists; until verified, treat protection as pending. A permanent `develop` branch adds little value for one developer and independently releasable phases, so it is not proposed. This is a specific lightweight Git workflow, not the historical GitFlow model with release/develop branches.

Branch names use lowercase descriptive words and `feature/`, `fix/`, `refactor/`, `docs/`, `test/`, `perf/`, `build/`, `ci/` or `chore/`. Never include assistant/tool names or identities. Start each independent branch from current `main`; dependent PRs declare their base and dependency. Avoid combining Redis, OTP and Kafka into one PR.

Proposed milestone tags: `v0.1.0` after the P1/P2 four-operation HTTP API baseline, `v0.2.0` authenticated access, `v0.3.0` confirmation, `v0.4.0` Redis, `v0.5.0` Kafka and `v0.6.0` only after a successful CDC phase. These are planning labels, not releases. A failed CDC feasibility review does not block retaining the working polling release.

Use squash merge by default with the final commit retaining all three required sections. Do not rewrite shared `main`. Revert faulty application changes through a new PR. For schema changes prefer forward corrections and compatible expand/contract migrations; a Git revert does not undo committed financial data or safely reverse every schema migration.

## Commit format

```text
Feat: Add atomic transfer confirmation

Bind approval to an immutable transfer intent so concurrent confirmations
cannot execute the same authorized transfer twice.

Key changes include:

- Add intent persistence and expiry validation.
- Commit code consumption and transfer execution together.
- Add replay and concurrent-confirmation integration tests.
- Update English and Persian authorization documents.
```

Use the appropriate capitalized type: `Feat`, `Fix`, `Refactor`, `Docs`, `Test`, `Perf`, `Build`, `CI` or `Chore`. Header is imperative; description explains why in 1–3 sentences; detailed bullets identify actual changes. Working commits and squash messages follow the same format.

## PR gates

Every PR states the concrete problem/result, scope, ADR and phase, schema/API changes, validation evidence, limits and rollback implications. Add meaningful tests for financial behavior and failures. Do not invent reviewer approval for a solo project; record self-review honestly.

Required CI evolves by phase: Java 21 wrapper build/tests at P0; HTTP contract, validation/error and SQL Server migration/integration tests at P1; security/confirmation tests at P3/P4; Redis fault tests at P5; Kafka and consumer failure tests at P6. Pin service images and document prerequisites. Default full verification should eventually be one documented command that provisions disposable dependencies or uses a clearly documented prerequisite command. Separate a fast unit command from full integration verification; a unit-only green build does not certify persistence.

Before merge: required CI green, invariant tests pass, no secrets/OTP values in artifacts, and both language documents updated. Performance PRs include matched baseline and after results, actual plans where relevant, and a correctness regression run. Documentation-only PRs need link/content checks, not unnecessary infrastructure startup.

## Documentation ownership

Repository documents now own the implementation plan and implemented behavior. The vault index points here and retains the historical discussion; avoid two independently edited canonical specifications. The original challenge remains the source for company requirements.

Recommended repository layout:

```text
docs/
  en/architecture.md
  fa/architecture.md
  adr/ADR-001.en.md
  adr/ADR-001.fa.md
  performance/Q02/<run-id>/
  evidence/phase-status.md
README.md
README.fa.md
```

Keep ADR IDs, phase IDs, query IDs, status labels and code identifiers stable across languages. Update paired documents in the same PR; translate meaning and examples, not only headings. Acceptance criteria and limitations must agree. Keep SQL/code blocks left-to-right and Persian explanation outside them. Record user-approved scope separately from proposed architecture, implemented code and measured evidence.

For each phase record commit/PR, date, tests actually run, environment, sanitized result location, known gaps and next gate. Do not mark a feature complete from a design note, stale report or compile success. No credentials, live OTPs, production data or company PDF enter public Git.
