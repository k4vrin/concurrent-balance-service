# V1 HTTP API contract

[فارسی](../fa/API-v1.md) · [Roadmap](Roadmap.md)

Scope selected by the user on 2026-10-22: the first functional version delivers all four balance operations through a JSON HTTP API backed by SQL Server. Endpoint shapes below are proposed until reviewed in CBS01/CBS23. No financial endpoint is implemented yet. V1 means the first functional API release; it is separate from Flyway migration `V1` and any Git tag.

## Proposed routes

| Method | Route | Request |
| --- | --- | --- |
| POST | `/api/v1/accounts/{accountId}/credits` | `transactionId`, `amountMinor`, `currency` |
| POST | `/api/v1/accounts/{accountId}/debits` | `transactionId`, `amountMinor`, `currency` |
| POST | `/api/v1/transfers` | `transactionId`, `sourceAccountId`, `destinationAccountId`, `amountMinor`, `currency` |
| GET | `/api/v1/accounts/{accountId}/balance` | Account ID in the path |

Amounts use integer minor units within the Java `long` range. Select one supported currency in CBS01; do not accept arbitrary currency codes. Mutations use the existing global transaction-ID namespace, including operation type and payload conflict detection. Syntax failures do not reserve an operation ID. Account initialization remains documented development/test setup, separate from these four routes.

Successful mutations return HTTP 200 and an explicit operation result containing its transaction ID, operation type, currency and committed outcome data. Replay returns the stored original result even after later operations change balances. Balance lookup returns the committed balance and currency. Specify the exact DTO fields, content types and examples during CBS23 before implementing the paths.

Proposed errors use a consistent ProblemDetail response with a stable application `code`: HTTP 400 for malformed input, invalid IDs/amounts, unsupported currency or self-transfer; 404 for an unknown account; 409 for transaction-ID payload conflict, insufficient funds or balance overflow; 503 for transient infrastructure unavailability or exhausted transient retries. Final codes and uncertain-commit recovery are reviewed in CBS01/CBS03/CBS23. A 503 or lost response must not claim that no commit happened. Retry with the same transaction ID to recover the durable outcome. Stack traces, credentials and internal SQL are excluded from responses.

## Delivery and acceptance

The existing Java interface is bootstrap scaffolding, not the V1 deliverable or a fixed public API. Keep financial transaction logic behind thin feature-based controllers with constructor injection. Internal service types and signatures may evolve to express outcomes and business failures; preserve the four operation semantics and document any deliberate departure from the original challenge's Java signatures. Do not remove the interface merely to move transaction logic into controllers.

Introduce the HTTP contract, DTO validation, error mapping and focused adapter tests at CBS23, immediately after CBS05. Build each durable operation as an HTTP-to-SQL Server slice at CBS10–15. No fake successful money response is an accepted implementation. Retain service-level concurrency and direct-SQL tests, and add real HTTP integration coverage for balance, mutations, replay, conflict, validation and failure rollback. The CBS20 core gate includes all four working routes and a reproducible Docker-backed API runbook with request/response examples.

V1 is initially a local development API on the existing loopback binding, with a trusted operator threat assumption documented explicitly. JWT, account ownership and role enforcement remain CBS24–25/P3, before shared or external access. V1 must not be described as a secured customer-facing service. Authentication failures later map to 401, authorization failures to 403, with current authorization required before replay. OTP remains P4; after confirmation is introduced, direct transfer routes must not bypass it. Redis, Kafka and CDC remain later stages.
