# CBS23 HTTP adapter evidence

[فارسی](../fa/HTTP-adapter-evidence.md) · [Contract](API-v1.md) · [Roadmap](Roadmap.md)

Implemented on 2026-10-23 as an assisted track step. Four routes delegate to typed `BalanceOperations`; the original `BalanceService` remains bootstrap scaffolding. The default boundary throws explicit unavailability. No successful financial operation, identity reservation, database schema or SQL Server behavior is implemented by this step.

Java 21.0.11, `./mvnw verify`: 200 tests, zero failures, errors or skips, BUILD SUCCESS, 9.772 seconds. This run includes 49 focused HTTP contract cases plus one new live-server smoke case that checks all four unavailable routes. The preceding 150 tests remain included. The test-only `smoke` profile excludes persistence; this run does not contact SQL Server.

Reproduce with a Java 21 `JAVA_HOME`:

```sh
./mvnw -Dtest=BalanceControllerTests test
./mvnw verify
```

`BalanceControllerTests` uses MockMvc and a mocked application boundary. It verifies command direction, exact case-sensitive transaction IDs, direct rial units, long range, strict scalar types, required fields, full UUID parsing, self-transfer, unknown fields, duplicate keys, trailing JSON, success/query DTO shape, rejection/conflict/admission/unavailability mapping and sanitized internal errors. Method/media negotiation also has stable problems. Repeating the same supplied outcome tests deterministic serialization only; both calls still reach the mock and no durable deduplication is proved.

`ConcurrentBalanceServiceApplicationTests` starts the real embedded HTTP server without persistence. A valid credit, debit, transfer and balance request each gets 503 `OPERATION_UNAVAILABLE`. This confirms the fallback cannot advertise successful money movement. Normal application startup still requires configured SQL Server; the smoke profile is test-only.

The initial focused run found an empty 415 body because scoped advice did not apply before controller selection. Global advice fixes that boundary; the subsequent complete run passed. A sandbox cache-lock failure before compilation supplied no test evidence.

Unverified work remains: runtime currency policy loading, SQL exception classification, commit boundaries, response persistence, replay/conflict enforcement, funding selection, balance/journal writes, real SQL Server races and failure recovery. The trusted loopback threat assumption remains; JWT/ownership/roles precede shared access. Business caps and pre-migration constraint/transaction review remain open. No task acceptance, independent learner mastery or core G1 acceptance is inferred from assistant-written tests.

Next: CBS06 versioned canonical request comparison, followed by CBS07–08 infrastructure/harness and CBS02–04 closure before CBS09 migration. CBS10–20 replaces the fallback with durable HTTP-to-SQL Server slices and proves the accounting/recovery behavior.

## Sealed response refinement, 2026-10-23

Mutation controllers and mapper now return `ResponseEntity<OperationResponseBody>`. The sealed body permits only OperationResponse and final OperationProblem, a ProblemDetail subclass. Rejection mapping remains a normal returned value. Strengthened HTTP assertions cover problem content type, status/title/instance and top-level extensions. A new direct serialization comparison proves the custom problem retains the original ProblemDetail JSON. The default about:blank type is omitted by the current serializer; the paired contract examples now reflect that behavior. Final Java 21.0.11 `./mvnw verify` passed 201 tests with zero failures/errors/skips in 7.216 seconds. No durable execution or acceptance status changed.
