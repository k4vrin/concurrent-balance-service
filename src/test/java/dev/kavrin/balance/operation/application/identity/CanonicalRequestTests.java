package dev.kavrin.balance.operation.application.identity;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks versioned canonical intent and retained-funding fingerprints against independent vectors.
 *
 * <p>Cases preserve exact identity distinctions, unambiguous encoding and defensive byte ownership.
 * Canonical values do not themselves provide durable idempotency or reserve transaction IDs.
 */
class CanonicalRequestTests {
    private static final AccountId A = id("00000000-0000-0000-0000-000000000001");
    private static final AccountId B = id("00000000-0000-0000-0000-000000000002");
    private static final AccountId C = id("00000000-0000-0000-0000-000000000003");
    private static final Money AMOUNT = new Money(100, Currency.getInstance("IRR"));

    @Test
    void equalCommandsHaveEqualCanonicalBytesAndHashes() {
        var first = transfer("first-id", A, B, AMOUNT, Optional.empty());
        var second = transfer("another-id", A, B, AMOUNT, Optional.empty());
        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(first.fingerprint().sha256()).isEqualTo(second.fingerprint().sha256());
        assertThat(first.fingerprint().hashCode()).isEqualTo(second.fingerprint().hashCode());
    }

    @Test
    void freezesVersionOneBytesWithAnIndependentGoldenVector() {
        var fingerprint = transfer("t1", A, B, AMOUNT, Optional.empty()).fingerprint();
        assertThat(fingerprint.version()).isEqualTo(1);
        // Fixed field lengths and the explicit absent-target/actor markers are part of the format.
        var expected = "0000000100000008"
                + "0000000772657175657374"
                + "000000085452414e53464552"
                + "ffffffff"
                + "0000002430303030303030302d303030302d303030302d303030302d303030303030303030303031"
                + "0000002430303030303030302d303030302d303030302d303030302d303030303030303030303032"
                + "00000003313030"
                + "00000003495252"
                + "ffffffff";
        assertThat(HexFormat.of().formatHex(fingerprint.bytes())).isEqualTo(expected);
        assertThat(fingerprint.sha256()).isEqualTo("9eac5bca4175ebd6aaa024fd362e313b1ae23cb7cf43e5f5fe4495b558a5a996");
    }

    @Test
    void changedAmountCurrencyAccountsDirectionAndActorChangeIdentity() {
        var baseline = transfer("id", A, B, AMOUNT, Optional.empty()).fingerprint();
        assertThat(transfer("id", A, B, new Money(101, AMOUNT.currency()), Optional.empty()).fingerprint()).isNotEqualTo(baseline);
        assertThat(transfer("id", A, B, new Money(100, Currency.getInstance("USD")), Optional.empty()).fingerprint()).isNotEqualTo(baseline);
        assertThat(transfer("id", C, B, AMOUNT, Optional.empty()).fingerprint()).isNotEqualTo(baseline);
        assertThat(transfer("id", A, C, AMOUNT, Optional.empty()).fingerprint()).isNotEqualTo(baseline);
        assertThat(transfer("id", B, A, AMOUNT, Optional.empty()).fingerprint()).isNotEqualTo(baseline);
        assertThat(transfer("id", A, B, AMOUNT, Optional.of("actor-1")).fingerprint()).isNotEqualTo(baseline);
    }

    @Test
    void operationTypesCannotShareIdentity() {
        var credit = CanonicalRequest.credit(new CreditCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        var debit = CanonicalRequest.debit(new DebitCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        var opening = CanonicalRequest.opening(A, AMOUNT, Optional.empty());
        assertThat(credit.fingerprint()).isNotEqualTo(debit.fingerprint()).isNotEqualTo(opening.fingerprint());
        assertThat(debit.fingerprint()).isNotEqualTo(opening.fingerprint());
    }

    @Test
    void uuidTextCaseDoesNotChangeParsedAccountIdentity() {
        var upper = id("ABCDEFAB-0000-0000-0000-000000000001");
        var lower = id("abcdefab-0000-0000-0000-000000000001");
        assertThat(transfer("id", upper, B, AMOUNT, Optional.empty()).fingerprint())
                .isEqualTo(transfer("id", lower, B, AMOUNT, Optional.empty()).fingerprint());
    }

    @Test
    void actorIdentityIsExactAndNotNormalized() {
        assertThat(transfer("id", A, B, AMOUNT, Optional.of("Actor")).fingerprint())
                .isNotEqualTo(transfer("id", A, B, AMOUNT, Optional.of("actor")).fingerprint())
                .isNotEqualTo(transfer("id", A, B, AMOUNT, Optional.of(" Actor ")).fingerprint());
        assertThat(transfer("id", A, B, AMOUNT, Optional.of("é")).fingerprint())
                .isNotEqualTo(transfer("id", A, B, AMOUNT, Optional.of("e\u0301")).fingerprint());
    }

    @Test
    void lengthPrefixesPreventDelimiterAndAbsentValueAmbiguity() {
        assertThat(CanonicalFingerprint.encode("a|b", "c")).isNotEqualTo(CanonicalFingerprint.encode("a", "b|c"));
        assertThat(CanonicalFingerprint.encode("a", "bc")).isNotEqualTo(CanonicalFingerprint.encode("ab", "c"));
        assertThat(CanonicalFingerprint.encode((String) null)).isNotEqualTo(CanonicalFingerprint.encode(""));
        assertThat(CanonicalFingerprint.encode("a")).isNotEqualTo(CanonicalFingerprint.encode("a", null));
        assertThat(CanonicalFingerprint.encode("شناسه", "🔑").bytes()).containsSequence("شناسه".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void canonicalBytesCannotBeMutatedThroughAccessor() {
        var fingerprint = transfer("id", A, B, AMOUNT, Optional.empty()).fingerprint();
        var originalHash = fingerprint.sha256();
        var bytes = fingerprint.bytes();
        bytes[0] = 42;
        assertThat(fingerprint.bytes()[0]).isZero();
        assertThat(fingerprint.sha256()).isEqualTo(originalHash);
    }

    @Test
    void retainedFundingSelectionIsDistinctFromCallerIntent() {
        var request = CanonicalRequest.credit(new CreditCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        var original = request.fundedFingerprint(new FundingSelection(B, "funding-v1"));
        assertThat(request.fundedFingerprint(new FundingSelection(B, "funding-v1"))).isEqualTo(original);
        assertThat(request.fundedFingerprint(new FundingSelection(C, "funding-v1"))).isNotEqualTo(original);
        assertThat(request.fundedFingerprint(new FundingSelection(B, "funding-v2"))).isNotEqualTo(original);
        assertThat(original).isNotEqualTo(request.fingerprint());
    }

    @Test
    void fundedIdentityIncludesTheFullRequestAndActor() {
        var credit = CanonicalRequest.credit(new CreditCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        var debit = CanonicalRequest.debit(new DebitCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        var actor = CanonicalRequest.credit(new CreditCommand(new TransactionId("id"), A, AMOUNT), Optional.of("actor"));
        var selection = new FundingSelection(B, "v1");
        assertThat(credit.fundedFingerprint(selection)).isNotEqualTo(debit.fundedFingerprint(selection))
                .isNotEqualTo(actor.fundedFingerprint(selection));
    }

    @Test
    void rejectsFundingForTransferOrSameAccount() {
        var transfer = transfer("id", A, B, AMOUNT, Optional.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> transfer.fundedFingerprint(new FundingSelection(C, "v1")));
        var credit = CanonicalRequest.credit(new CreditCommand(new TransactionId("id"), A, AMOUNT), Optional.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> credit.fundedFingerprint(new FundingSelection(A, "v1")));
    }

    @Test
    void openingRequiresPositiveAmountAndRetainsItsCounterpart() {
        var opening = CanonicalRequest.opening(A, AMOUNT, Optional.empty());
        assertThat(opening.fundedFingerprint(new FundingSelection(B, "opening-v1")))
                .isNotEqualTo(opening.fundedFingerprint(new FundingSelection(C, "opening-v1")));
        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalRequest.opening(A, new Money(0, AMOUNT.currency()), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalRequest.opening(A, new Money(-1, AMOUNT.currency()), Optional.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\ud800", "\udc00"})
    void rejectsBlankOrMalformedActorIdentity(String actor) {
        assertThatIllegalArgumentException().isThrownBy(() -> transfer("id", A, B, AMOUNT, Optional.of(actor)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\ud800", "\udc00"})
    void rejectsInvalidFundingPolicyVersion(String version) {
        assertThatIllegalArgumentException().isThrownBy(() -> new FundingSelection(B, version));
    }

    @Test
    void rejectsMissingValues() {
        assertThatNullPointerException().isThrownBy(() -> CanonicalRequest.credit(null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> transfer("id", A, B, AMOUNT, null));
        assertThatNullPointerException().isThrownBy(() -> new FundingSelection(null, "v1"));
        assertThatNullPointerException().isThrownBy(() -> new FundingSelection(B, null));
        assertThatNullPointerException().isThrownBy(() -> CanonicalRequest.opening(null, AMOUNT, Optional.empty()));
    }

    private static CanonicalRequest transfer(String transactionId, AccountId source, AccountId destination,
            Money amount, Optional<String> actor) {
        return CanonicalRequest.transfer(new TransferCommand(new TransactionId(transactionId), source, destination, amount), actor);
    }

    private static AccountId id(String text) { return new AccountId(UUID.fromString(text)); }
}
