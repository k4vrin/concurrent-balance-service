# V1 HTTP API reference

[فارسی](../fa/API-v1.md) · [Runnable requests](../../http/balance-service.http) · [Local walkthrough](HTTP-samples.md) · [Core acceptance](Core-acceptance.md)

This is the implemented CBS20 core contract. The four routes execute against SQL Server when the database/schema are configured and `EXECUTION_POLICY_ENABLED=true`. The server binds to `http://127.0.0.1:8080` by default. It assumes a trusted local operator; authentication and account ownership are later scope. There is no public account-creation or opening-balance endpoint.

## Routes and request bodies

POST requests require `Content-Type: application/json`. Success uses `application/json`; problems use `application/problem+json`. Use `Accept: application/json, application/problem+json` when testing both paths.

| Method | Route | Required JSON fields |
| --- | --- | --- |
| POST | `/api/v1/accounts/{accountId}/credits` | `transactionId`, `amountRial`, `currency` |
| POST | `/api/v1/accounts/{accountId}/debits` | `transactionId`, `amountRial`, `currency` |
| POST | `/api/v1/transfers` | `transactionId`, `sourceAccountId`, `destinationAccountId`, `amountRial`, `currency` |
| GET | `/api/v1/accounts/{accountId}/balance` | No body. |

Credit/debit name the customer account in the path. The server chooses their funding counterpart from configuration; callers cannot supply a funding account. Transfer moves funds between two distinct customer accounts without a funding leg. All participant accounts must exist, be active and satisfy the configured category/currency rules.

Credit example, after provisioning the account:

```http
POST http://127.0.0.1:8080/api/v1/accounts/10000000-0000-4000-8000-000000000001/credits
Content-Type: application/json

{"transactionId":"demo-v1-credit-alice","amountRial":1000,"currency":"IRR"}
```

Transfer example:

```json
{
  "transactionId": "demo-v1-transfer",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "20000000-0000-4000-8000-000000000002",
  "amountRial": 250,
  "currency": "IRR"
}
```

## Parsing and money rules

- Every listed field is required and non-null. Unknown properties, duplicate keys, trailing JSON values and malformed JSON are rejected.
- `transactionId` is a case-sensitive JSON string of 1–128 ASCII letters, digits, `.`, `_`, `:` or `-`. No trimming, normalization, header alias or generated default is applied.
- Account IDs use the full UUID group shape. Hexadecimal case is irrelevant to UUID identity. Shortened groups are invalid; transfer compares parsed UUID values to reject self-transfer.
- `amountRial` must be a positive JSON integer token within signed `long`. Zero, negatives, `1.0`, exponent notation, numeric strings, booleans and overflow are invalid. One unit is one rial; there is no multiplication by 100 or silent rounding.
- `currency` is an exact uppercase ISO code recognized by Java. Current execution policy admits **IRR only**. Recognizing another currency's syntax does not enable it or define another unit contract.
- The default business limits are **10000000000000 rials** for each transaction and each resulting customer balance. A positive representable amount above the transaction cap passes parsing, then receives a retained business rejection. Checked arithmetic separately prevents overflow.

Parsing/shape failures return 400 `INVALID_REQUEST` before application execution and reserve no operation identity. Unsupported new-operation currency returns 400 `UNSUPPORTED_CURRENCY` without a retained operation. Existing terminal outcomes are resolved before current admission policy, so changing policy cannot change a matching replay.

## Successful responses

A mutation returns **200 only after its transaction commits**. The minimal acknowledgement has exactly these fields; it does not include current balances, journal IDs or persistence details:

```json
{"transactionId":"demo-v1-transfer","operationType":"TRANSFER","currency":"IRR","status":"SUCCEEDED"}
```

Balance lookup returns the authoritative committed projection:

```json
{"accountId":"10000000-0000-4000-8000-000000000001","balanceRial":750,"currency":"IRR"}
```

The balance shown depends on executed history; 750 is the final Alice balance in the [HTTP walkthrough](HTTP-samples.md). Missing accounts return 404 and are never presented as zero. Balance lookup creates no operation.

## Error contract

Problems include HTTP `status`, standard `title`, sanitized `detail`, request `instance` and stable `code`. The default `about:blank` type is omitted by the current serializer. Retained mutation rejections also include `transactionId`, `operationType` and `currency`.

| HTTP | Code | Meaning / identity effect |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Malformed input/shape, invalid UUID/amount, self-transfer; no retained operation. |
| 400 | `UNSUPPORTED_CURRENCY` | New-operation policy admission failure; no retained operation. |
| 404 | `ACCOUNT_NOT_FOUND` | Unknown balance lookup, or a retained mutation rejection for a missing requested account. |
| 409 | `TRANSACTION_ID_CONFLICT` | Existing ID with a different canonical request; original result remains unchanged. |
| 409 | `INSUFFICIENT_FUNDS` | Retained debit/transfer rejection; no financial history. |
| 409 | `TRANSACTION_LIMIT_EXCEEDED` | Retained rejection above the transaction cap. |
| 409 | `BALANCE_LIMIT_EXCEEDED` | Retained rejection above the customer-balance cap. |
| 409 | `BALANCE_OVERFLOW` | Retained rejection when a resulting projection cannot be represented. |
| 503 | `OPERATION_UNAVAILABLE` | Disabled mutation execution, classified infrastructure failure, exhausted recovery or unresolved commit outcome. |
| 500 | `INTERNAL_ERROR` | Unexpected defect; no assumption that nothing committed. |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED` / `NOT_ACCEPTABLE` / `UNSUPPORTED_MEDIA_TYPE` | HTTP method/representation negotiation failure. |

Example retained rejection after a 651-rial debit against 650 rials:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Available funds are insufficient.",
  "instance": "/api/v1/accounts/10000000-0000-4000-8000-000000000001/debits",
  "code": "INSUFFICIENT_FUNDS",
  "transactionId": "demo-v1-rejected-debit",
  "operationType": "DEBIT",
  "currency": "IRR"
}
```

Never branch on `detail` text; use status/code. Internal SQL, credentials and exception traces are not response fields. Wrong account category/status/currency and missing internal funding are not an account-management API; investigate server configuration and fixtures instead of assuming every technical failure is a customer rejection.

## Replay and failure recovery

`transactionId` has one global namespace across mutation types. The retained canonical request includes type, customer/participant IDs, amount and currency. Equal canonical intent returns the original status, content type and serialized body; a differing payload/type conflicts. Replay creates no journal or posting and does not rebuild the result using current balances or policy.

Terminal business rejection is durable too. If `demo-v1-rejected-debit` requests 651 against 650, then a separate credit raises the balance to 750, retrying that rejected ID still returns the original 409. A new intentional financial attempt needs a new ID.

Every successful mutation atomically commits its operation, balanced immutable journal/postings, all projections and original response. Rejection commits only the retained decision. Known rollback can permit bounded whole-transaction retry with the same identity; an uncertain commit permits recovery reads rather than blind reexecution. **A 503, 500, timeout or lost response does not establish that no commit happened. Preserve the ID and original payload to recover the outcome.** See [retry/recovery](Retry-execution.md).

## Configuration and verification

Startup requires migrated schema and runtime-only `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Enabling mutations additionally requires a valid funding account UUID/version; [the sample walkthrough](HTTP-samples.md) supplies the commands. Balance lookup remains available with mutation execution disabled when persistence is configured. Runtime never applies migrations automatically.

The [core acceptance guide](Core-acceptance.md) records actual HTTP-to-SQL Server, rollback, replay, concurrency and process-restart evidence. Historical development checkpoints remain in [implementation status](Implementation-status.md) and the individual evidence pages. This current reference supersedes their earlier unavailable-route descriptions.
