# UUID bits, unsigned comparison and account lock order

[فارسی](../fa/UUID-lock-order.md) · [Architecture](Architecture.md) · [Transaction protocol](Transaction-protocol.md)

Educational guide, 2026-10-23. This explains the implemented AccountId/AccountLockOrder comparator and its six tests. The comparator is unit-tested; SQL Server locking and ID mapping are not implemented. Ordering identifiers does not by itself acquire locks or guarantee freedom from every deadlock.

## 1. Why this appears in our application

AccountId gives a UUID a domain meaning and keeps it distinct from other identifiers. All account writers need one predictable order before acquiring their complete set of account locks. We selected descending unsigned UUID order, independent of transfer direction. This is an ordering convention, not a business ranking or timestamp.

The confusing phrase “high-half sign bit” means the leftmost bit of the first 64 bits returned from a UUID. The UUID itself is an identifier; Java's signed long representation creates the sign interpretation of each half.

## 2. Hexadecimal digits are not individual bits

Binary is base 2 and uses 0/1. Hexadecimal is base 16 and uses 0–9/a–f. One hex digit encodes exactly four bits because 16 = 2^4. Uppercase A–F has the same numeric meaning as lowercase.

| Hex | Binary | Decimal |
| --- | --- | --- |
| 0 | 0000 | 0 |
| 1 | 0001 | 1 |
| 2 | 0010 | 2 |
| 3 | 0011 | 3 |
| 4 | 0100 | 4 |
| 5 | 0101 | 5 |
| 6 | 0110 | 6 |
| 7 | 0111 | 7 |
| 8 | 1000 | 8 |
| 9 | 1001 | 9 |
| a | 1010 | 10 |
| b | 1011 | 11 |
| c | 1100 | 12 |
| d | 1101 | 13 |
| e | 1110 | 14 |
| f | 1111 | 15 |

For example, `8` is a hex digit, while `1000` is its four-bit representation. The leftmost hex digit is 8, but its leftmost bit is 1. `0x` marks a hexadecimal Java literal; it is not stored as part of the bits.

Sixteen hex digits represent 64 bits. Both examples below have 16 groups of four bits; preserve leading zeroes when reasoning about a fixed-width value:

```text
8000000000000000
1000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
^
leftmost bit = 1

7fffffffffffffff
0111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111
^
leftmost bit = 0
```

## 3. Signed long uses two's complement

Java long has 64 bits and a signed range from -2^63 to 2^63 - 1. In that signed interpretation the leftmost bit is the sign bit: 0 means zero/positive, 1 means negative. This is two's complement, not a separate minus sign followed by a magnitude.

For a 64-bit pattern interpreted unsigned as U:

```text
If U < 2^63:  signed value = U
Otherwise:   signed value = U - 2^64
```

| 64-bit hex pattern | Signed value | Unsigned value |
| --- | --- | --- |
| 0000000000000000 | 0 | 0 |
| 7fffffffffffffff | 9,223,372,036,854,775,807 | 9,223,372,036,854,775,807 |
| 8000000000000000 | -9,223,372,036,854,775,808 | 9,223,372,036,854,775,808 |
| ffffffffffffffff | -1 | 18,446,744,073,709,551,615 |

Therefore `0x7fff_ffff_ffff_ffffL` is Long.MAX_VALUE and `0x8000_0000_0000_0000L` is Long.MIN_VALUE. The underscores help reading and do not change the value. A hex literal with the sign bit set can represent a negative long without a written minus sign.

Signed and unsigned interpretation use the same bits. Long.compareUnsigned does not clear, flip or remove the first bit; it uses all 64 bits without treating the first as a negative sign. Java does not need a separate unsigned-long primitive to perform this comparison. For displaying that interpretation use Long.toUnsignedString; an ordinary decimal print of a long remains signed.

## 4. A UUID has two 64-bit halves

A UUID has 128 bits, normally displayed as 32 hex digits in groups 8-4-4-4-12. Hyphens are formatting. Its first three groups contain 16 hex digits, the high half; the last two contain the other 16, the low half. High/low here mean significance, not memory address or machine byte order.

```text
UUID:      80000000-0000-0000-0000-000000000000
High half: 8000000000000000   (first 16 hex digits, ignoring hyphens)
Low half:  0000000000000000   (last 16 hex digits)

UUID:      00000000-0000-0000-8000-000000000000
High half: 0000000000000000
Low half:  8000000000000000
```

Java exposes the halves through getMostSignificantBits and getLeastSignificantBits. Each accessor returns a signed long containing the relevant unchanged bits. A half can print negative while the UUID remains a perfectly representable identifier.

Conceptually, our ascending unsigned 128-bit order compares:

```text
unsignedUUID = unsigned(highHalf) * 2^64 + unsigned(lowHalf)
```

We do not calculate that large integer at runtime. Comparing the high half first, then the low half on a tie, gives the same order. If high halves differ, low halves cannot overturn their result.

## 5. Signed comparison gives a different order

For high halves `8000000000000000` and `7fffffffffffffff`:

```text
Signed:    -9223372036854775808 < 9223372036854775807
Unsigned:   9223372036854775808 > 9223372036854775807
```

Ordinary signed comparison would therefore violate our selected unsigned order at this boundary. The high-half test deliberately makes the lower half of the smaller high-half UUID all f's; that also checks that lower bits do not override the high-half decision. The tie-breaker test keeps high halves equal and crosses the sign bit only in the low half.

Signed ordering could also be a consistent locking convention if everyone deliberately chose it. Our issue is mixing conventions. We explicitly selected unsigned descending order and must implement that choice identically in all writers.

## 6. Reading the comparator

This is the implemented comparison logic from account/application/AccountLockOrder.java:

```java
private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
    int comparison = Long.compareUnsigned(
            left.getMostSignificantBits(), right.getMostSignificantBits());
    return comparison != 0 ? comparison : Long.compareUnsigned(
            left.getLeastSignificantBits(), right.getLeastSignificantBits());
};

public static final Comparator<AccountId> DESCENDING =
        Comparator.comparing(AccountId::value, UNSIGNED_UUID_ORDER).reversed();
```

1. Compare high halves as unsigned values.
2. If they differ, use that result immediately.
3. If they match, compare low halves unsigned.
4. Extract UUID values from AccountId wrappers.
5. Reverse the complete ascending comparator to get descending order.

A comparator result has ordering meaning, not monetary meaning:

| Result | Placement of the left argument |
| --- | --- |
| Negative | Before the right argument |
| Zero | Equal for ordering |
| Positive | After the right argument |

With DESCENDING, a larger UUID comes first, so comparing the larger UUID to the smaller one returns a negative result. This does not mean the UUID is a negative business value.

## 7. What each test protects

| Test | Risk it detects |
| --- | --- |
| comparesHighBitsAsUnsigned | Signed comparison incorrectly places the 0x8000 high-half pattern below 0x7fff |
| comparesLowBitsAsUnsignedWhenHighBitsMatch | Correct high comparison but a signed or missing low-half tie-breaker |
| equalityUsesUuidValue | Wrapper object identity accidentally affects equal UUIDs |
| oppositeTransferDirectionsProduceSameOrder | Source/destination position affects ordering |
| ordersBothHalvesAcrossExtremesTransitively | Inconsistent extreme-value or tie-break ordering; checks all pairs in the selected finite fixture |
| rejectsMissingAccountIds | A null participant is silently accepted by null-first/null-last sorting |

These examples exercise the comparator contract over important fixtures, not an exhaustive proof over every UUID. They do not test account existence, authorization, database lock acquisition or database concurrency. Nil/all-f test UUIDs are bit-pattern fixtures; their presence does not claim generation of valid version-4 account IDs or real persisted accounts. The current wrapper does not restrict UUID versions.

## 8. Connection to deadlock prevention

Given A starting with `1000...` and B starting with `f000...`, both A→B and B→A sort to B, A. A source-first algorithm could acquire A/B in opposite order; the shared comparator removes that particular deliberate ordering inversion.

The future persistence path must collect every customer/system participant, deduplicate it, sort with AccountLockOrder.DESCENDING and execute locking lookups sequentially in the same transaction. Do not acquire one account lock before constructing the complete order. The comparator belongs to application locking policy; AccountId does not implement Comparable and claim a universal business order.

SQL Server uniqueidentifier ordering has its own semantics. Do not replace this comparator with implicit Java UUID natural order, SQL GUID sorting or an assumption that an ORDER BY result controls physical lock acquisition. Indexed locking SQL, lock lifetime and retries require real SQL Server tests. Other index/constraint/resources can still deadlock even with correct account order.

## 9. Runnable Java 21 example

After building the project, save this as UuidOrderingDemo.java outside the repository source tree:

```java
import java.util.UUID;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.domain.AccountId;

public class UuidOrderingDemo {
    public static void main(String[] args) {
        long high = 0x8000_0000_0000_0000L;
        long low = 0x7fff_ffff_ffff_ffffL;

        System.out.println("signed high: " + high);
        System.out.println("unsigned high: " + Long.toUnsignedString(high));
        System.out.println("signed low: " + low);
        System.out.println("signed comparison: " + Long.compare(high, low));
        System.out.println("unsigned comparison: " + Long.compareUnsigned(high, low));

        var first = new AccountId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        var second = new AccountId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));
        System.out.println("descending UUID comparison: "
                + AccountLockOrder.DESCENDING.compare(first, second));
    }
}
```

From the repository root, run with Java 21:

```sh
java --class-path target/classes /path/to/UuidOrderingDemo.java
```

Expected output, checked using Java 21 on 2026-10-23:

```text
signed high: -9223372036854775808
unsigned high: 9223372036854775808
signed low: 9223372036854775807
signed comparison: -1
unsigned comparison: 1
descending UUID comparison: -1
```

The numeric comparison results are not account balances. No account is created and no database connection is needed by this example.

## 10. Interview explanation and common mistakes

A concise defense: “AccountId wraps UUID for type safety. We compare its two halves unsigned, high first, then reverse for one shared descending order. Both transfer directions and funding-account writers must acquire locks in that order. It reduces account-order deadlocks; whole-transaction retry and SQL tests still handle other failures.”

Avoid confusing a hex digit with a bit, treating an entire UUID as a signed monetary long, clearing the sign bit, comparing only the high half, returning a subtraction result as a comparator, or changing sort convention between writers. UUID uniqueness, database uniqueness, authorization and idempotency are separate guarantees.

## Sources and implementation links

Reviewed 2026-10-23: [Java 21 Long](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html), [Java 21 UUID](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html), [SQL Server uniqueidentifier](https://learn.microsoft.com/en-us/sql/t-sql/data-types/uniqueidentifier-transact-sql). The arithmetic above derives the chosen convention; vendor docs do not select that project policy for us.

[Comparator source](../../src/main/java/dev/kavrin/balance/account/application/AccountLockOrder.java) · [Comparator tests](../../src/test/java/dev/kavrin/balance/account/application/AccountLockOrderTests.java) · [Account ID](../../src/main/java/dev/kavrin/balance/account/domain/AccountId.java)
