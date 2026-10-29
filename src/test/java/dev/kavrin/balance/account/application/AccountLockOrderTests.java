package dev.kavrin.balance.account.application;

import java.util.List;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks the unsigned, descending UUID account order independently of transfer direction.
 *
 * <p>These portable comparator cases do not acquire database locks or establish SQL Server GUID ordering.
 */
class AccountLockOrderTests {

    // Crossing the high-half sign bit exposes signed-long comparison: unsigned 0x8000...
    // must precede 0x7fff... in descending order, regardless of the lower half.
    @Test
    void comparesHighBitsAsUnsigned() {
        var high = id("80000000-0000-0000-0000-000000000000");
        var low = id("7fffffff-ffff-ffff-ffff-ffffffffffff");

        assertThat(AccountLockOrder.DESCENDING.compare(high, low)).isNegative();
        assertThat(AccountLockOrder.DESCENDING.compare(low, high)).isPositive();
    }

    // Equal high halves force the lower-half tie-breaker, which must also be unsigned.
    @Test
    void comparesLowBitsAsUnsignedWhenHighBitsMatch() {
        var high = id("00000000-0000-0000-8000-000000000000");
        var low = id("00000000-0000-0000-7fff-ffffffffffff");

        assertThat(AccountLockOrder.DESCENDING.compare(high, low)).isNegative();
        assertThat(AccountLockOrder.DESCENDING.compare(low, high)).isPositive();
    }

    // Separately constructed wrappers for the same UUID must compare equally;
    // object identity must never affect the lock order.
    @Test
    void equalityUsesUuidValue() {
        var text = "e0000000-0000-4000-8000-000000000001";

        assertThat(AccountLockOrder.DESCENDING.compare(id(text), id(text))).isZero();
    }

    // Reversing source and destination must not reverse lock acquisition order.
    // This checks the ordering policy, not actual database locking or deadlock freedom.
    @Test
    void oppositeTransferDirectionsProduceSameOrder() {
        var first = id("10000000-0000-4000-8000-000000000001");
        var second = id("f0000000-0000-4000-8000-000000000002");

        assertThat(List.of(first, second).stream().sorted(AccountLockOrder.DESCENDING).toList())
                .containsExactly(second, first);
        assertThat(List.of(second, first).stream().sorted(AccountLockOrder.DESCENDING).toList())
                .containsExactly(second, first);
    }

    // Extreme values exercise both unsigned halves. Checking every ordered pair
    // catches inconsistent tie-breakers that an adjacent-pair check could miss.
    @Test
    void ordersBothHalvesAcrossExtremesTransitively() {
        var expected = List.of(
                id("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                id("80000000-0000-0000-0000-000000000000"),
                id("7fffffff-ffff-ffff-ffff-ffffffffffff"),
                id("00000000-0000-0000-8000-000000000000"),
                id("00000000-0000-0000-7fff-ffffffffffff"),
                id("00000000-0000-0000-0000-000000000000"));

        assertThat(expected.reversed().stream().sorted(AccountLockOrder.DESCENDING).toList())
                .containsExactlyElementsOf(expected);
        for (int first = 0; first < expected.size(); first++) {
            for (int second = first + 1; second < expected.size(); second++) {
                assertThat(AccountLockOrder.DESCENDING.compare(
                        expected.get(first), expected.get(second))).isNegative();
            }
        }
    }

    // A missing participant is invalid; sorting it first or last would hide a caller error.
    @Test
    void rejectsMissingAccountIds() {
        var present = id("10000000-0000-4000-8000-000000000001");

        assertThatNullPointerException()
                .isThrownBy(() -> AccountLockOrder.DESCENDING.compare(null, present));
        assertThatNullPointerException()
                .isThrownBy(() -> AccountLockOrder.DESCENDING.compare(present, null));
    }

    private AccountId id(String value) {
        return new AccountId(UUID.fromString(value));
    }
}
