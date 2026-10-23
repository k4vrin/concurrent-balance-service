> Current review disposition, 2026-10-23: CBS02–04 engineering review is closed for entry to CBS09. [The implementation contract](CBS09-contract.md) selects storage, lock SQL, constraints, retry defaults and authority. Earlier open-choice entries below are historical unless retained in that contract. Migration/entity/financial verification belongs to CBS09–20; no business DDL has been applied.

# Exact transaction-ID storage review

[فارسی](../fa/Transaction-id-storage.md) · [Canonical request](Canonical-request.md) · [Persistence review](Persistence-review.md) · [Test runbook](SQL-server-tests.md)

Status, 2026-10-23: reviewed and tested engineering candidate for CBS02–04. This is temporary-table SQL Server evidence. No business migration, runtime identity repository, stored canonical decoder or durable replay is implemented. The remaining transaction/schema/permissions review still precedes CBS09.

## The contract

The accepted TransactionId domain is 1–128 ASCII letters, digits, `.`, `_`, `:` or `-`. Preserve the exact text. Do not trim, change case or apply Unicode normalization. Account UUIDs, internal operation row IDs and canonical request fingerprints are separate concepts.

| Input | Required behavior |
| --- | --- |
| `transfer-001` and `Transfer-001` | Two valid, distinct identities |
| An exact second `transfer-001` | Named global unique constraint violation |
| `transfer-001 ` or ` transfer-001` | Invalid input; never a normalized alias |
| Fullwidth `Ａ`, Persian letters/digits, emoji | Invalid input; never convert to an ASCII substitute |
| Empty, null, NUL, whitespace or 129 characters | Invalid input |

An operations table's transaction-ID uniqueness must span all mutation types. Do not add operation type to its unique key. This key locates retained intent and outcome. The canonical bytes then decide whether the repeated ID has matching financial meaning. Uniqueness alone cannot distinguish matching replay from conflicting reuse.

## Candidate column and constraints

Use Unicode `nvarchar(256)` with explicit `Latin1_General_100_BIN2`, NOT NULL, a unique constraint, a byte-length CHECK and a character CHECK. For eventual operations DDL, use a stable unique name such as `uq_operations_transaction_id`; the fixture uses a generated suffix to prevent tempdb name collisions between test runs.

The source-controlled fixture generates the following shape with its own table/constraint names. This is illustrative DDL, not a command to apply a business schema:

```sql
CREATE TABLE <review_fixture> (
    transaction_id nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT <unique_name> UNIQUE (transaction_id),
    CHECK (DATALENGTH(transaction_id) BETWEEN 2 AND 256),
    CHECK (
        CONVERT(varbinary(512), TRANSLATE(
            transaction_id COLLATE Latin1_General_100_BIN2,
            N'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-',
            REPLICATE(N'A', 66)
        )) = CONVERT(varbinary(512),
            REPLICATE(N'A', DATALENGTH(transaction_id) / 2))
    )
);
```

Each accepted ASCII character occupies two bytes in nvarchar. The length CHECK therefore permits 1–128 accepted characters. NOT NULL is separate because SQL CHECK can allow an unknown result. The unique index compares valid stored IDs case-sensitively. The column is deliberately wider than the domain: if an overlong bound value is silently truncated to 256 code units, it still violates the 128-character constraint. This costs additional declared index width; no throughput/storage overhead has been measured.

The wider column does not detect a caller that deliberately casts its input to nvarchar(128) before insertion. No constraint can recover discarded input. Runtime writers must bind the original validated Unicode value and avoid lossy casts. The fixture uses JDBC `setNString`; validate through TransactionId before lookup as well as insert. SQL equality still pads trailing spaces for arbitrary invalid lookup text, so the column is not permission to query with unvalidated IDs.

## Why the character check uses bytes

TRANSLATE maps each of the 66 allowed characters to `A`. An allowed ID of length n becomes n copies of `A`. The check converts that result and the expected marker string to varbinary and compares them. A disallowed character survives translation, so its bytes differ. DATALENGTH counts bytes without hiding trailing spaces or NUL. There is no conversion to a non-Unicode code page and no hash-only identity comparison.

For example, `Ab:-9` becomes `AAAAA` and passes. `Ab 9` contains a space whose bytes remain different from `A`, so it fails. `A` followed by NUL followed by `B` also fails. The explicit binary collation keeps the operation separate from linguistic equivalence rules. [Microsoft documents TRANSLATE](https://learn.microsoft.com/en-us/sql/t-sql/functions/translate-transact-sql?view=sql-server-ver17) as character mapping; the tests below verify this exact expression on the pinned SQL Server 2022 instance.

A BIN2 unique column without the syntax CHECK would still allow unwanted strings and SQL trailing-space comparison behavior. A varchar conversion before validation could lose the original Unicode input. LIKE-based filtering also needs special care around control characters. This candidate instead checks the preserved Unicode value against its exact permitted character set.

## Competing insert example

The integration schedule uses two separate connections and the same ID, `competing-id`:

1. Connection A disables autocommit and inserts the ID without committing.
2. Connection B attempts the same insert with LOCK_TIMEOUT 250. It receives 1222 while A is open. B rolls back the whole attempt.
3. If A commits, B's next attempt receives 2627 containing the generated identity constraint name.
4. If A rolls back instead, B can insert and commit that ID.

This supports reservation-before-account-locking in the proposed protocol. It does not implement replay: after a named duplicate, the future coordinator must leave/roll back the failed transaction and recover the committed canonical intent/outcome through a fresh boundary. Never classify every 2627 or every integrity exception as idempotency. Another unique key or a CHECK/FK failure needs its own handling. A lock timeout remains a technical failure and must not become a retained customer rejection.

## Tests and limits

[SqlServerTransactionIdIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTransactionIdIT.java) and [TransactionIdSqlFixture](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/TransactionIdSqlFixture.java) use the guarded cbs_test connection and generated global temporary tables. They add no privileges and do not create accounts/operations/journals/postings.

The cases cover exact round trips, both case variants, all 66 allowed characters in one ID, 1/128-character boundaries, named duplicate errors, 24 invalid inputs, NOT NULL, disabled truncation warnings and both competing-insert outcomes. A separate set-based query evaluates all 65,536 single UTF-16 code units embedded between ASCII characters: exactly 66 satisfy the character predicate, with zero disagreements against the expected ASCII ranges. That query is one test, not 65,536 JUnit cases. Supplementary-character rejection is also tested through an actual emoji insert. It is not exhaustive testing of every multi-character Unicode sequence.

This verifies the candidate's character/uniqueness behavior through native JDBC. Final operations DDL must preserve these constraints, explicit collation and Unicode binding, then rerun the suite against the real migration. HTTP-to-database replay, same-ID/different-canonical-intent handling, uncertain commit, migration/runtime permissions, index plans and complete financial atomicity remain separate work. The SQL Server ARM64 environment remains an unsupported emulation experiment.

## Transaction-ID review checkpoint, 2026-10-23

The tested engineering candidate uses nvarchar(256) with explicit BIN2 collation, NOT NULL, 1–128-character length/allowlist checks and named global uniqueness. Unicode JDBC binding preserves invalid input for rejection. Thirty-one new real cases include exhaustive single-BMP-code-unit predicate checking and competing inserts. Full Java 21 profile verification passed 240 portable plus 40 SQL Server tests, zero failures/errors/skips, in 12.756 seconds; the new class took 2.091 seconds. Earlier counts are historical. No business migration, runtime repository or durable replay is added. Next review is the UUID account-lock adapter and complete four-table transaction/permissions contract; formal learner acceptance remains open.
