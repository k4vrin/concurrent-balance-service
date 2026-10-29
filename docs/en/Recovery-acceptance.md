# CBS19 commit and response recovery acceptance

[فارسی](../fa/Recovery-acceptance.md) · [Retry protocol](Retry-execution.md) · [Core acceptance](Core-acceptance.md)

## Failure boundaries

An exception before commit must leave no operation or financial effect. A failed acknowledgement after commit may already have a durable outcome. A lost HTTP response must not cause another posting when the caller retries the same identity. These boundaries apply to terminal rejection as well as success.

`SqlServerRecoveryIT` adds 24 real SQL Server cases through two independent HTTP contexts and pools. Test-only wrappers select explicit failure boundaries; the actual transaction manager, SQL writes, committed history and independent reconstruction remain authoritative.

| Fault | Variants | Required result |
| --- | --- | --- |
| Before commit | Every mutation, success/rejection | Delegate rolls back the actual transaction, then throws. First request returns 503 with zero retained operation/history and unchanged balances; peer same-ID retry completes once. |
| After commit | Every mutation, success/rejection | Delegate commits actual writes and then throws. Fresh recovery returns the original retained outcome; no second reservation occurs. |
| Hidden recovery result | Every mutation, success/rejection | After a real commit, three actual recovery reads are deliberately masked as absent. Request returns 503 without reexecution. Peer retry recovers the committed result. |
| Replaced HTTP response | Every mutation, success/rejection | A test filter buffers the real completed response and replaces it with HTTP 503. Another application returns the original stored status and bytes with one effect. |

Each case requires one reservation in the faulted request. Success has one retained operation, one balanced journal and two postings; rejection has one operation and no financial history. Independent SQL verifies all relevant balances against wider posting totals. Rejected commands replay unchanged after top-up. Counterpart selection and identity remain in the unchanged production flow.

## Verification

The focused run passed 24 SQL Server cases plus 421 portable tests in 24.980 seconds. Full Java 21 Maven `-Psqlserver-it verify` passed **421 portable + 212 real SQL Server tests**, with **zero failures/errors/skips**, in **01:05 min** on 2026-10-29. Fresh Surefire/Failsafe XML totals were checked.

These are controlled acknowledgement, read-visibility and HTTP-response simulations around real database effects. They do not claim a physical TCP partition at an exact commit instruction or separate-host crash isolation. The separate packaged-process scenario in [CBS20](Core-acceptance.md) adds a real lock timeout/rollback, same-ID retry and process-restart recovery without these wrappers.
