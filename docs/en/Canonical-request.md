# CBS06 canonical request and funding fingerprints

[فارسی](../fa/Canonical-request.md) · [Identity protocol](Idempotency-protocol.md) · [Operation walkthrough](Operation-flow.md)

For the reasons, alternatives, system-design terminology and worked timelines, read [the design companion](Canonical-design.md).

Implemented portable foundation, 2026-10-23. This document defines the current version-1 byte format and its application API. It does not accept the remaining draft transaction/storage protocol, reserve identity, load policy, authorize a caller or implement replay. The HTTP adapter still delegates to the unavailable fallback.

## Why canonicalize a request?

A retry can change JSON whitespace, field order or UUID letter case while expressing the same validated intent. Conversely, a new amount or destination under a retained transaction ID must conflict. Comparing raw HTTP JSON cannot express that boundary reliably. Canonicalization operates on typed commands after parsing, preserving semantic values and account roles.

The transaction ID is the lookup key, not part of the compared payload. Two distinct transaction IDs may have identical canonical bytes and still be distinct financial attempts. This implementation neither deduplicates across IDs nor infers human intent. Transport timing, tracing, HTTP formatting, response timestamps and OTP secrets are not inputs.

## Public types and responsibilities

| Type | Responsibility |
| --- | --- |
| [CanonicalRequest](../../src/main/java/dev/kavrin/balance/operation/application/identity/CanonicalRequest.java) | Build request identity from credit/debit/transfer commands or positive internal opening intent |
| [CanonicalFingerprint](../../src/main/java/dev/kavrin/balance/operation/application/identity/CanonicalFingerprint.java) | Immutable versioned bytes, lowercase SHA-256 hex and byte-based equality |
| [FundingSelection](../../src/main/java/dev/kavrin/balance/operation/application/policy/FundingSelection.java) | Explicit counterpart AccountId and exact funding-policy version to retain with execution metadata |

Factories accept an explicit `Optional<String>` actor binding. `Optional.empty()` represents the current trusted local context, with no fabricated customer identity. A present actor is exact, nonblank valid Unicode. No trimming, case folding or Unicode normalization occurs. Its future source must be trusted authentication, not an arbitrary JSON actor field. There is no authentication implementation here.

Account IDs use parsed UUID values serialized with `UUID.toString()`. Full accepted upper/lowercase UUID text therefore has the same identity. Account source/destination/target roles stay distinct and are not sorted into lock order. Amount uses canonical positive base-10 long text; currency uses `Currency.getCurrencyCode()`. Supported-currency admission stays separate and mutable policy is not consulted by these factories.

The opening factory represents positive internal opening intent and requires a distinct funding counterpart for its funded fingerprint. It does not add a public endpoint or implement account initialization. Zero-initialized accounts need no nonzero opening intent; zero and negative values are rejected by this factory. Account categories, actual counterpart existence and journal persistence remain later responsibilities.

## Fixed version-1 encoding

All integer headers are signed 32-bit big-endian values. Bytes contain:

1. Format version, currently `1`.
2. Field count.
3. Each field in fixed order, with a signed length followed by UTF-8 bytes. `-1` means absent, with no following bytes. `0` means present empty text. Length counts bytes, not Java characters.

The request has eight fields:

| Position | Value |
| --- | --- |
| 1 | Literal `request` |
| 2 | Enum name CREDIT, DEBIT, TRANSFER or OPENING |
| 3 | Target UUID for credit/debit/opening, absent for transfer |
| 4 | Source UUID for transfer, absent otherwise |
| 5 | Destination UUID for transfer, absent otherwise |
| 6 | Positive amount in canonical base-10 notation |
| 7 | Currency code |
| 8 | Exact actor ID, or absent marker |

Length framing prevents ambiguity such as `(a\|b, c)` versus `(a, b\|c)`, and distinguishes absence from an empty field. UTF-8 encoding rejects malformed UTF-16, including unpaired surrogate characters, rather than replacing them and potentially collapsing distinct identities. Semantic actor/policy fields additionally reject blank values. Delimiters, Unicode and nonblank whitespace-containing identities are preserved as supplied.

Funded execution has four fields under the same versioned framing:

1. Literal `funded-execution`, separating its namespace from request identity.
2. Lowercase hexadecimal representation of the **entire request canonical byte array**.
3. Retained funding counterpart UUID.
4. Exact nonblank funding-policy version.

Including the entire nested request, rather than only its digest, preserves byte-authoritative comparison. Version 1 includes every semantic field and does not use enum ordinals, default encodings, object hash codes or a serializer-dependent JSON layout. Changing this format requires a new version and compatibility review; never silently redefine version 1. A stored-format decoder/version dispatcher remains future persistence work, so these classes currently produce only version 1.

`bytes()` returns a defensive copy. `equals` and `hashCode` use the complete canonical bytes. `sha256()` is a stable companion digest, not an authorization token or a substitute for canonical comparison. No durable uniqueness or hash-collision recovery is implemented by these values.

## Transfer example

With the existing AccountId, Money, command and java.util types imported, the example from the operation walkthrough becomes:

```java
var command = new TransferCommand(
        new TransactionId("Tx-001"),
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
        new Money(100, Currency.getInstance("IRR")));

var request = CanonicalRequest.transfer(command, Optional.empty());
var fingerprint = request.fingerprint();
// fingerprint.version() == 1
// fingerprint.sha256():
// 9eac5bca4175ebd6aaa024fd362e313b1ae23cb7cf43e5f5fe4495b558a5a996
```

The test suite freezes the exact bytes and this hash, independently checked with Python's UTF-8, big-endian packing and hashlib SHA-256. Changing only Tx-001 to another valid ID preserves this payload fingerprint. Reversing source/destination or changing amount, currency, actor or operation type changes it. Transfers already name both effective accounts; calling fundedFingerprint on a transfer fails instead of introducing an unrelated system account.

## Funding selection and replay order

For credit, debit and internal opening, the customer request does not name its server-selected funding counterpart. Use two comparisons rather than reevaluating current funding policy during replay:

```java
var credit = new CreditCommand(
        new TransactionId("credit-001"), customerAccountId,
        new Money(100, Currency.getInstance("IRR")));
var request = CanonicalRequest.credit(credit, Optional.empty());
var retainedSelection = new FundingSelection(fundingAccountId, "funding-v1");
var executionFingerprint = request.fundedFingerprint(retainedSelection);
```

Assume the customer and funding IDs are distinct typed values. These lines construct metadata only. Changing the counterpart or policy version changes executionFingerprint while leaving request.fingerprint() unchanged. Credit, debit and opening remain distinct because the nested request includes operation type. A counterpart equal to the target is rejected, but the factory cannot verify account existence, category or external liquidity.

The future coordinator must first locate a retained operation by transaction ID and compare its request fingerprint plus canonical bytes. For matching terminal identity, replay the original response using retained execution metadata; do not select today's funding account or reevaluate current currency support. For a new operation only, resolve and retain the chosen counterpart and policy version, construct the funded fingerprint, and apply the reviewed reservation/locking/commit protocol. A retained terminal rejection with no actual posting still needs its original request/result; missing requested accounts must not prevent retaining that rejection.

If funding-v2 selects account C after funding-v1 selected B, retrying the original ID must keep B and its original response. A genuinely new ID may use C and funding-v2. The classes let us distinguish these cases, but no coordinator or persistence enforces the sequence yet. Null selection, transfer funding and same-target funding fail locally.

## Evidence and limits

[CanonicalRequestTests](../../src/test/java/dev/kavrin/balance/operation/application/identity/CanonicalRequestTests.java) covers equal semantic requests with different lookup IDs; fixed version-1 bytes/hash; account direction/type/amount/currency/actor differences; UUID case equivalence; exact actor case/whitespace/Unicode identity; delimiter/null framing; UTF-8 validity; defensive copying; funding counterpart/policy differences; internal opening identity and required inputs.

With Java 21:

```sh
./mvnw -Dtest=CanonicalRequestTests test
./mvnw verify
```

These are pure portable tests. There is no SQL Server comparison/collation evidence, persisted canonical decoding, identity reservation, database-backed policy selection, real replay, authorization or financial execution. The pre-migration CBS02–04 review and CBS07–08 database environment/harness remain required. No independent mastery or formal task acceptance is inferred from assistant-written code.

Verification on 2026-10-23 with Java 21.0.11: full `./mvnw verify` passed 223 tests, zero failures/errors/skips, in 5.839 seconds. This includes 22 new canonical cases. The tests first failed compilation before the implementation was added, then passed. No SQL Server tests ran.
