package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.operation.domain.TransactionId;
import java.sql.SQLException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies exact transaction-ID storage and uniqueness with the real SQL Server driver.
 *
 * <p>Unicode binding, binary collation, the ASCII allowlist and competing inserts are tested directly.
 * Temporary fixtures isolate storage evidence from financial operation execution.
 */
class SqlServerTransactionIdIT {
    private static SqlServerTestDatabase database;

    @BeforeAll
    static void requireRealDatabase() throws Exception {
        database = SqlServerTestDatabase.fromEnvironment();
        try (var connection = database.openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void validIdsRoundTripExactlyAndCaseVariantsCoexist() throws Exception {
        var ids = new String[] {"a", "A", "transfer-001", "Transfer-001",
                TransactionIdSqlFixture.ALLOWED, "x".repeat(128)};
        try (var fixture = TransactionIdSqlFixture.create(database); var connection = database.openConnection()) {
            for (var id : ids) {
                assertThat(new TransactionId(id).value()).isEqualTo(id);
                fixture.insert(connection, id);
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT transaction_id FROM " + fixture.tableName())) {
                var stored = new java.util.ArrayList<String>();
                while (result.next()) {
                    stored.add(result.getNString(1));
                }
                assertThat(stored).containsExactlyInAnyOrder(ids);
            }
        }
    }

    @Test
    void exactDuplicateViolatesTheNamedGlobalIdentityConstraint() throws Exception {
        try (var fixture = TransactionIdSqlFixture.create(database); var connection = database.openConnection()) {
            fixture.insert(connection, "same-id");
            assertThatThrownBy(() -> fixture.insert(connection, "same-id"))
                    .isInstanceOfSatisfying(SQLException.class, failure -> {
                        assertThat(failure.getErrorCode()).isEqualTo(2627);
                        assertThat(failure.getMessage()).contains(fixture.uniqueConstraint());
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void directUnicodeBindingRejectsInvalidDomainValues(String id) throws Exception {
        assertThatThrownBy(() -> new TransactionId(id)).isInstanceOf(IllegalArgumentException.class);
        try (var fixture = TransactionIdSqlFixture.create(database); var connection = database.openConnection()) {
            assertThatThrownBy(() -> fixture.insert(connection, id))
                    .isInstanceOfSatisfying(SQLException.class, failure ->
                            assertThat(failure.getErrorCode()).isIn(547, 2628, 8152));
        }
    }

    static Stream<String> invalidIds() {
        return Stream.of("", " ", " leading", "trailing ", "a b", "a\tb", "a\nb", "a\rb",
                "\0", "a\0b", "a\0", "\u200B", "\u00A0", "Ａ", "é", "ا", "١", "😀",
                "a/b", "a%b", "a[b", "x".repeat(129), "x".repeat(257), "x".repeat(128) + " ");
    }

    @Test
    void columnHeadroomRejectsOverlongInputEvenWithTruncationWarningsDisabled() throws Exception {
        try (var fixture = TransactionIdSqlFixture.create(database); var connection = database.openConnection();
                var statement = connection.createStatement()) {
            statement.execute("SET ANSI_WARNINGS OFF");
            // Truncating to 256 code units must not turn a 257-character input into a valid 128-character ID.
            assertThatThrownBy(() -> fixture.insert(connection, "x".repeat(257)))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(547));
            assertThatThrownBy(() -> fixture.insert(connection, "x".repeat(128) + " ".repeat(129)))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(547));
        }
    }

    @Test
    void nullIsRejectedByNotNullRatherThanAnUnknownCheck() throws Exception {
        try (var fixture = TransactionIdSqlFixture.create(database); var connection = database.openConnection()) {
            assertThatThrownBy(() -> fixture.insert(connection, null))
                    .isInstanceOfSatisfying(SQLException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(515));
        }
    }

    @Test
    void everySingleBmpCodeUnitMatchesTheAsciiAllowlist() throws Exception {
        // Exhaust all 65,536 UTF-16 code units inside an ID; do not rely only on selected invalid examples.
        var sql = """
                WITH digits(n) AS (SELECT n FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9)) d(n)),
                numbers(n) AS (
                  SELECT a.n + 10*b.n + 100*c.n + 1000*d.n + 10000*e.n
                  FROM digits a CROSS JOIN digits b CROSS JOIN digits c CROSS JOIN digits d CROSS JOIN digits e
                ), candidates AS (
                  SELECT n, N'A' + NCHAR(n) + N'A' AS candidate FROM numbers WHERE n < 65536
                )
                SELECT COUNT(*), SUM(CASE WHEN
                """ + "(" + TransactionIdSqlFixture.allowedCharacters("candidate") + ") THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN (CASE WHEN " + TransactionIdSqlFixture.allowedCharacters("candidate")
                + " THEN 1 ELSE 0 END) <> (CASE WHEN n BETWEEN 65 AND 90 OR n BETWEEN 97 AND 122 "
                + "OR n BETWEEN 48 AND 57 OR n IN (45,46,58,95) THEN 1 ELSE 0 END) "
                + "THEN 1 ELSE 0 END) FROM candidates";
        try (var connection = database.openConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(65536);
            assertThat(result.getInt(2)).isEqualTo(66);
            assertThat(result.getInt(3)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void competingReservationWaitsForTheFirstTransactionOutcome(boolean commit) throws Exception {
        try (var fixture = TransactionIdSqlFixture.create(database);
                var first = database.openConnection(); var contender = database.openConnection()) {
            first.setAutoCommit(false);
            contender.setAutoCommit(false);
            try {
                fixture.insert(first, "competing-id");
                try (var statement = contender.createStatement()) {
                    statement.execute("SET LOCK_TIMEOUT 250");
                }
                assertThatThrownBy(() -> fixture.insert(contender, "competing-id"))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                contender.rollback();
                if (commit) {
                    first.commit();
                    assertThatThrownBy(() -> fixture.insert(contender, "competing-id"))
                            .isInstanceOfSatisfying(SQLException.class, failure -> {
                                assertThat(failure.getErrorCode()).isEqualTo(2627);
                                assertThat(failure.getMessage()).contains(fixture.uniqueConstraint());
                            });
                } else {
                    first.rollback();
                    fixture.insert(contender, "competing-id");
                    contender.commit();
                }
            } finally {
                first.rollback();
                contender.rollback();
            }
        }
    }
}
