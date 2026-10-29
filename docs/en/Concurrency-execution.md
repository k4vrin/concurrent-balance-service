# CBS16 distinct-operation concurrency evidence

[فارسی](../fa/Concurrency-execution.md) · [Requirements](Requirements.md) · [Retry recovery](Retry-execution.md) · [Implementation status](Implementation-status.md)

## Start with competing spending decisions

An account with 100 rials receives eight simultaneous debit requests for 30 rials each, all with distinct transaction IDs. Only three can succeed. The five losing requests must retain insufficient-funds decisions, create no journals and replay those same decisions even after the account receives more money. A final balance alone cannot prove this: retained outcomes, history and projections must agree.

`SqlServerDistinctConcurrencyIT` adds this case and four related cases through the real HTTP API and SQL Server adapters. Production financial code, schema and configuration are unchanged.

## Coordinating actual concurrent attempts

`ConcurrentHttpBatch` creates one worker per request and uses a ten-second client barrier before sending. Each HTTP request has a thirty-second timeout; the whole batch has a thirty-five-second wait. Every worker result is collected in input order, and worker exceptions/cancellations are aggregated as test failures.

A test-only `AccountStore` spy adds a second barrier before the first account-lock call in each request. The barrier has a four-second timeout, within the existing five-second transaction timeout, and every initial attempt must arrive. The spy then invokes the real adapter: lock acquisition, participant ordering, funds checks and database writes remain production behavior. Subsequent retries do not rejoin the barrier. The tested batches contain eight or nine requests, fitting the existing ten-connection pool. The spy is reset after each batch.

Fixtures create zero-balance accounts through the restricted initializer identity. Any opening funds use the real credit handler and balanced journal path. Each class has its own funding account, and each scenario has a unique transaction-ID prefix so earlier retained test history cannot change expected counts.

## Scenarios and exact expected results

| Scenario | Concurrent commands | Required committed result |
| --- | --- | --- |
| Shared customer credits | Eight distinct credits of 25 rials | Eight successes, customer +200, funding +200, eight journals and sixteen postings. |
| Shared funding, separate customers | Eight distinct credits of 25 rials | Every customer +25, funding +200, eight successes/journals and sixteen postings. |
| Competing debits | Eight debits of 30 from a 100-rial customer | Three successes, five retained rejections, balance 10, funding -90, three journals and six postings. |
| Competing transfers | Eight transfers of 30 from a 100-rial source to separate zero-balance recipients | Three successes and five rejections; source 10, recipients total 90, funding unchanged, three journals and six postings. |
| Mixed mutations | Three credits of 25, three debits of 10 and three transfers of 15 from a shared 1000-rial source | Nine successes; source remains 1000, recipient rises from 1000 to 1045, funding +45; nine journals and eighteen postings. |

SQL checks require the exact total/success/rejection counts and zero committed PENDING rows. Every succeeded operation must have one journal, exactly two postings and equal debit/credit totals using `decimal(38,0)` arithmetic. Rejected operations must have no journal or postings.

After all workers finish, an independent SQL query reconstructs each participant's projection from immutable postings, respecting the customer/funding normal side. Zero-history accounts reconstruct to zero. Each original request is then replayed concurrently and must return the identical HTTP status and response bytes with unchanged history counts. Debit and transfer losers are replayed after a 300-rial top-up; the retained rejections must survive the newly available funds. Reconciliation runs again after replay.

## Verification, 2026-10-29

Full Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **421 portable + 162 real SQL Server tests**, with **zero failures/errors/skips**, in **45.449 seconds**. Fresh Surefire/Failsafe XML totals were checked.

The focused first run passed all five new integration cases, alongside 421 portable tests, in 17.931 seconds. The final regression includes the additional reconciliation checks after concurrent replay. Both new types pass Javadoc/doclint. The compiler syntax-tree scan checked all 147 Java files and found zero nested/local/anonymous types. Changed-document links, whitespace and exact credential scans of logs/reports passed.

## Limits and next work

This is one application instance using separate concurrent request transactions against the existing dedicated `cbs_test` database. Barriers establish overlapping entry to locking, but the suite cannot enumerate every scheduler/database interleaving or certify throughput. Reconciliation occurs after writers finish, not during arbitrary concurrent history changes.

Cross-instance identical/conflicting transaction-ID races, opposite-direction transfers, independent-account progress while another account is blocked, and broader response/commit faults remain CBS17–19 work. The local SQL Server ARM64 emulation remains unsupported by Microsoft. No formal learner acceptance or production-readiness claim follows from these test results. This checkpoint contains the CBS16 distinct-operation concurrency tests and paired guides; CBS15 was separately committed and pushed as `6f6cc61`.
