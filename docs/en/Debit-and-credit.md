# Debit and credit across account types

[فارسی](../fa/Debit-and-credit.md) · [Ledger design](Ledger.md) · [Accepted ADR-007](../adr/ADR-007-double-entry-ledger.en.md)

This guide explains the accounting rules behind `Posting.balanceDelta`. Read every example from the service's point of view. All amounts are rials: 100 IRR means 100 rials.

## Start with a customer balance

A customer has 500 rials. A credit posting of 100 increases their balance to 600. A debit posting of 100 decreases it to 400.

```text
Customer credit: 500 + 100 = 600
Customer debit:  500 - 100 = 400
```

These are the rules for our customer accounts. Other account types can use the opposite rule, so a posting needs an account category to determine its balance effect.

## What debit and credit mean

Debit is the left accounting side; credit is the right accounting side. Neither word universally means increase or decrease. The account's normal side determines which entry increases its recorded balance.

The standard account families follow this table. “Normal balance” describes the usual side of a balance, not a guarantee that a balance can never move to the other side. These conventions are explained in [OpenStax, normal balances](https://openstax.org/books/principles-financial-accounting/pages/3-1-describe-principles-assumptions-and-concepts-of-accounting-and-their-relationship-to-financial-statements).

| Account family | What it represents | Debit | Credit | Normal balance |
| --- | --- | --- | --- | --- |
| Asset | Resources or amounts receivable | Increase | Decrease | Debit |
| Liability | Amounts owed to others | Decrease | Increase | Credit |
| Equity | Owners' residual interest | Decrease | Increase | Credit |
| Revenue | Income earned | Decrease | Increase | Credit |
| Expense | Costs incurred | Increase | Decrease | Debit |

For example, cash is an asset, customer deposits are liabilities on a bank's books, fees earned are revenue, and rent is an expense. These are general accounting categories; our application currently implements only the two categories described below. The table does not add new enum values or product features.

## Why a customer balance is a liability

Ask whose books we are recording. If a customer has 500 rials with the service, the service owes that customer 500 rials. That obligation is a liability for the service. Increasing it requires a credit; decreasing it requires a debit.

From the customer's own point of view, money held at a bank is an asset. The same relationship appears on different sides in the two parties' books. Our ledger records the service's books, so `CUSTOMER_LIABILITY` is credit-normal.

Crediting a customer does not mean the service earned revenue. It increases the amount owed to that customer.

## The two categories currently implemented

| Category | Debit 100 | Credit 100 | Balance policy |
| --- | --- | --- | --- |
| `CUSTOMER_LIABILITY` | Change of -100 | Change of +100 | Must remain nonnegative |
| `FUNDING_CLEARING` | Change of +100 | Change of -100 | Signed values within the `long` range |

`FUNDING_CLEARING` is our synthetic internal counterpart with a chosen debit-normal convention. Its balance tracks the other side of customer credits and debits. A signed balance is permitted by ADR-007. This is a project policy, not proof of available external funds, and not a rule that all real clearing accounts must follow.

A real cash asset, settlement payable, fee revenue account or overdraft would need its own reviewed meaning and rules. The name “clearing” alone cannot determine an account's normal side.

The normal side and the allowed balance range are separate decisions. Our customer nonnegative rule is a business constraint. Debit-normal does not automatically mean that negative balances are forbidden.

## Worked journals

A journal groups the complete accounting entry. Each posting is one line with an account, a side and a positive amount. Every example below has total debits equal to total credits.

### Credit a customer with 100 rials

The service selects the funding counterpart. Starting balances are customer 500 and funding 500.

| Posting account | Side | Posting amount | Balance change | Resulting balance |
| --- | --- | ---: | ---: | ---: |
| Funding | Debit | 100 | +100 | 600 |
| Customer | Credit | 100 | +100 | 600 |

Both displayed balances increase. The journal still balances because debit and credit totals are each 100. Adding the two signed balance changes is not the accounting balance check when the accounts use different normal sides.

### Debit a customer by 100 rials

Starting balances are customer 500 and funding 500.

| Posting account | Side | Posting amount | Balance change | Resulting balance |
| --- | --- | ---: | ---: | ---: |
| Customer | Debit | 100 | -100 | 400 |
| Funding | Credit | 100 | -100 | 400 |

Both balances decrease, and total debits still equal total credits. The customer's resulting balance must satisfy the nonnegative rule.

### Transfer 100 rials between customers

Starting balances are customer A 500 and customer B 200.

| Posting account | Side | Posting amount | Balance change | Resulting balance |
| --- | --- | ---: | ---: | ---: |
| Customer A | Debit | 100 | -100 | 400 |
| Customer B | Credit | 100 | +100 | 300 |

Both accounts are customer liabilities, so the transfer reduces one obligation and increases the other. No funding posting is needed for this transfer.

### Examples of additional account families

These are educational examples, not implemented use cases or accepted extensions to V1.

| Event | Debit line | Credit line |
| --- | --- | --- |
| Owner contributes 100 cash | Cash asset 100, increase | Owner equity 100, increase |
| Business pays rent of 100 | Rent expense 100, increase | Cash asset 100, decrease |
| Service earns a fee of 10 charged to a customer balance | Customer liability 10, decrease | Fee revenue 10, increase |

An account that offsets another account, called a contra account, usually has the opposite normal side. Accumulated depreciation is a common example of a credit-normal contra-asset. A future complete chart of accounts would need explicit policies for these exceptions; the current two-category enum does not model them. See [AccountingCoach, normal balances and contra accounts](https://www.accountingcoach.com/debits-and-credits/explanation).

## How this maps to Posting.balanceDelta

The amount stored in a posting is positive. `balanceDelta` returns a signed `Money` value for the balance projection. It preserves the currency.

```java
var irr = Currency.getInstance("IRR");
var amount = new Money(100, irr);
var posting = new Posting(customerId, PostingSide.DEBIT, amount);

Money delta = posting.balanceDelta(AccountCategory.CUSTOMER_LIABILITY);
// delta is Money(-100, IRR). A balance of 500 would become 400.
```

Here `customerId` is an existing `AccountId`. This excerpt illustrates domain calculation only. The method does not change an account, acquire a lock, persist data, check sufficient funds, or verify that the supplied category belongs to the posting's account. The caller must resolve the actual account and use its category.

For our debit-normal category, the balance change is debit amounts minus credit amounts. For our credit-normal category, it is credit amounts minus debit amounts. These are two conventions for storing account balances; the journal always checks total debits against total credits independently.

When a journal contains several postings for the same account, the projection path must aggregate their net effect using exact arithmetic and validate the final balance. Applying each line separately could reject a valid final result because of an intermediate negative value or overflow. Journal.projectBalances now implements this net calculation. Financial persistence remains pending.

## Common misunderstandings

- A debit is not always money leaving an account. It increases an asset or our debit-normal funding balance.
- A credit is not always income. Crediting a customer liability increases what the service owes the customer.
- A balanced journal does not prove the business operation is correct. Correct accounts, currency, authorization, sufficient funds and operation outcome still need checks.
- A posting amount remains positive. Its side and the account category determine the signed balance effect.
- Reversing a financial mistake requires a new accounting entry. Existing posting history remains immutable.

## Code and related reading

- [Posting and balanceDelta](../../src/main/java/dev/kavrin/balance/ledger/domain/Posting.java)
- [Account categories and balance policy](../../src/main/java/dev/kavrin/balance/account/domain/AccountCategory.java)
- [Journal validation](../../src/main/java/dev/kavrin/balance/ledger/domain/Journal.java)
- [Ledger design](Ledger.md) and [transaction protocol](Transaction-protocol.md)

Documentation added on 2026-10-23. This guide describes implemented domain rules and marks future behavior explicitly; it does not provide SQL Server or end-to-end financial evidence.
