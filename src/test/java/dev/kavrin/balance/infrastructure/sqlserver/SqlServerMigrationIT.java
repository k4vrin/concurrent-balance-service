package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the reviewed migration, named constraints and restricted permissions on real SQL Server.
 *
 * <p>Direct SQL exercises invalid row shapes and immutable history boundaries under the dedicated
 * runtime, migration and initializer identities. Schema checks do not prove cross-row financial invariants.
 */
class SqlServerMigrationIT {
    private static SqlServerTestDatabase runtime;
    private static SqlServerTestDatabase migrator;
    private static SqlServerTestDatabase initializer;

    @BeforeAll
    static void requireMigratedTestDatabaseAndZeroAccounts() throws Exception {
        runtime = SqlServerTestDatabase.fromEnvironment();
        migrator = SqlServerTestDatabase.fromEnvironment("SQLSERVER_MIGRATION_TEST");
        initializer = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST");
        migration().validate();
        try (var connection = initializer.openConnection()) {
            seedZero(connection, MigrationSqlFixture.A, "IRR");
            seedZero(connection, MigrationSqlFixture.B, "IRR");
            seedZero(connection, MigrationSqlFixture.USD, "USD");
        }
    }

    @Test
    void flywayValidatesAndRerunsWithoutReapplyingV1() {
        var flyway = migration();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThatThrownBy(flyway::clean).hasMessageContaining("cleanDisabled");
    }

    @Test
    void actualSchemaHasFourTablesNamedIndexesAndEnabledNonCascadingCurrencyFks() throws Exception {
        try (var connection = migrator.openConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(4);
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM sys.foreign_keys WHERE name IN "
                    + "('fk_journals_operation_currency','fk_postings_journal_currency','fk_postings_account_currency') "
                    + "AND is_disabled=0 AND is_not_trusted=0 AND delete_referential_action=0")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(3);
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM sys.indexes WHERE name IN "
                    + "('uq_operations_transaction_id','uq_journals_operation_id','ix_postings_account_currency')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(3);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidOperations")
    void invalidDirectOperationWriteFailsItsNamedConstraint(Map<String, String> changes, String constraint) throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> MigrationSqlFixture.operation(connection, changes))
                        .isInstanceOfSatisfying(SQLException.class, failure -> {
                            assertThat(failure.getErrorCode()).isEqualTo(547);
                            assertThat(failure.getMessage()).contains(constraint);
                        });
            } finally {
                connection.rollback();
            }
        }
    }

    static Stream<Arguments> invalidOperations() {
        var rejectedWithoutReason = new HashMap<>(MigrationSqlFixture.rejection());
        rejectedWithoutReason.put("rejection_reason", "NULL");
        var rejectedBadVersion = new HashMap<>(MigrationSqlFixture.rejection());
        rejectedBadVersion.put("response_format_version", "2");
        var rejectedOldCompletion = new HashMap<>(MigrationSqlFixture.rejection());
        rejectedOldCompletion.put("completed_at", "'19000101'");
        return Stream.of(
                bad("transaction_id", "N'a b'", "transaction_id_characters"),
                bad("transaction_id", "N'a' + NCHAR(0)", "transaction_id_characters"),
                bad("transaction_id", "N'Ａ'", "transaction_id_characters"),
                bad("transaction_id", "N'bad '", "transaction_id_characters"),
                bad("transaction_id", "REPLICATE(N'x',129)", "transaction_id_length"),
                bad("transaction_id", "N''", "transaction_id_length"),
                bad("operation_type", "'TRANSFER '", "type"),
                bad("source_account_id", "NULL", "shape"),
                bad("destination_account_id", "'" + MigrationSqlFixture.A + "'", "shape"),
                bad("amount", "0", "amount"), bad("amount", "-1", "amount"),
                bad("currency", "N'irr'", "currency"),
                bad("canonical_format_version", "2", "canonical"),
                bad("canonical_request", "0x", "canonical"),
                bad("canonical_fingerprint", "0x00", "canonical"),
                bad("execution_policy_version", "N' '", "policy"),
                bad("funding_account_id", "NEWID()", "funding"),
                bad("completed_at", "SYSUTCDATETIME()", "outcome"),
                bad("state", "'SUCCEEDED'", "outcome"),
                Arguments.of(rejectedWithoutReason, "ck_operations_outcome"),
                Arguments.of(rejectedBadVersion, "ck_operations_outcome"),
                Arguments.of(rejectedOldCompletion, "ck_operations_completion_time"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"transaction_id", "operation_type", "amount", "currency", "canonical_request",
            "canonical_fingerprint", "execution_policy_version", "state"})
    void requiredOperationFieldsCannotBypassChecksThroughNull(String column) throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> MigrationSqlFixture.operation(connection, Map.of(column, "NULL")))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(515));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void validTerminalRowsOverCapRejectionAndCaseSensitiveIdentityAreStorable() throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                var lower = "case-" + UUID.randomUUID();
                MigrationSqlFixture.operation(connection, Map.of("transaction_id", "N'" + lower + "'"));
                MigrationSqlFixture.operation(connection, Map.of("transaction_id", "N'" + lower.toUpperCase(java.util.Locale.ROOT) + "'"));
                assertThatThrownBy(() -> MigrationSqlFixture.operation(connection, Map.of("transaction_id", "N'" + lower + "'")))
                        .isInstanceOfSatisfying(SQLException.class, failure -> {
                            assertThat(failure.getErrorCode()).isEqualTo(2627);
                            assertThat(failure.getMessage()).contains("uq_operations_transaction_id");
                        });
                var rejection = new HashMap<>(MigrationSqlFixture.rejection());
                rejection.put("amount", "10000000000001");
                MigrationSqlFixture.operation(connection, rejection);
                MigrationSqlFixture.operation(connection, Map.of("state", "'SUCCEEDED'", "completed_at", "DATEADD(second,1,SYSUTCDATETIME())",
                        "response_format_version", "1", "http_status", "200", "response_content_type", "'application/json'", "response_body", "0x7B7D"));
                MigrationSqlFixture.operation(connection, Map.of("amount", "9223372036854775807"));
            } finally {
                connection.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDIT", "DEBIT", "OPENING"})
    void otherOperationShapesRequireFundingForSuccess(String type) throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                var shape = new HashMap<String, String>();
                shape.put("operation_type", "'" + type + "'");
                shape.put(type.equals("DEBIT") ? "destination_account_id" : "source_account_id", "NULL");
                MigrationSqlFixture.operation(connection, shape);
                shape.putAll(Map.of("state", "'SUCCEEDED'", "completed_at", "DATEADD(second,1,SYSUTCDATETIME())",
                        "response_format_version", "1", "http_status", "200", "response_content_type", "'application/json'", "response_body", "0x7B7D"));
                assertThatThrownBy(() -> MigrationSqlFixture.operation(connection, shape))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getMessage()).contains("ck_operations_success_funding"));
                shape.putAll(Map.of("funding_account_id", "NEWID()", "funding_policy_version", "N'funding-v1'", "funded_fingerprint", "HASHBYTES('SHA2_256',0x01)"));
                MigrationSqlFixture.operation(connection, shape);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void requestedMissingAccountRejectionDoesNotNeedAnAccountFk() throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                var values = new HashMap<>(MigrationSqlFixture.rejection());
                values.put("source_account_id", "NEWID()");
                values.put("rejection_reason", "'ACCOUNT_NOT_FOUND'");
                MigrationSqlFixture.operation(connection, values);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void postingsEnforceCurrencyReferencesAndPositiveImmutableLineShape() throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                var operation = MigrationSqlFixture.operation(connection, Map.of());
                expectConstraint(() -> MigrationSqlFixture.execute(connection,
                        "INSERT dbo.journals (journal_id,operation_id,currency) VALUES (NEWID(),'" + operation + "',N'USD')"),
                        "fk_journals_operation_currency");
                var journal = MigrationSqlFixture.journal(connection, operation);
                MigrationSqlFixture.posting(connection, journal, 1, MigrationSqlFixture.A, "IRR", "DEBIT", Long.MAX_VALUE);
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 2, MigrationSqlFixture.USD, "IRR", "CREDIT", 1), "fk_postings_account_currency");
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 2, MigrationSqlFixture.USD, "USD", "CREDIT", 1), "fk_postings_journal_currency");
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 2, UUID.randomUUID().toString(), "IRR", "CREDIT", 1), "fk_postings_account_currency");
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 0, MigrationSqlFixture.B, "IRR", "CREDIT", 1), "ck_postings_line");
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 2, MigrationSqlFixture.B, "IRR", "WRONG", 1), "ck_postings_side");
                expectConstraint(() -> MigrationSqlFixture.posting(connection, journal, 2, MigrationSqlFixture.B, "IRR", "CREDIT", 0), "ck_postings_amount");
                assertThatThrownBy(() -> MigrationSqlFixture.journal(connection, operation))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getMessage()).contains("uq_journals_operation_id"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void initializerRejectsNegativeCustomerBalanceAndAllowsSignedFundingBounds() throws Exception {
        try (var connection = initializer.openConnection()) {
            connection.setAutoCommit(false);
            try {
                expectConstraint(() -> MigrationSqlFixture.execute(connection, "INSERT dbo.accounts (account_id,category,currency,balance) "
                        + "VALUES (NEWID(),'CUSTOMER_LIABILITY',N'IRR',-1)"), "ck_accounts_balance");
                MigrationSqlFixture.execute(connection, "INSERT dbo.accounts (account_id,category,currency,balance) "
                        + "VALUES (NEWID(),'FUNDING_CLEARING',N'IRR',-9223372036854775808),"
                        + "(NEWID(),'FUNDING_CLEARING',N'IRR',9223372036854775807)");
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void rollbackRemovesItsOperationJournalAndPostingTogether() throws Exception {
        UUID operation;
        UUID journal;
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                operation = MigrationSqlFixture.operation(connection, Map.of());
                journal = MigrationSqlFixture.journal(connection, operation);
                MigrationSqlFixture.posting(connection, journal, 1, MigrationSqlFixture.A, "IRR", "DEBIT", 100);
            } finally {
                connection.rollback();
            }
        }
        try (var connection = runtime.openConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.operations WHERE operation_id='" + operation
                        + "'),(SELECT COUNT(*) FROM dbo.journals WHERE journal_id='" + journal
                        + "'),(SELECT COUNT(*) FROM dbo.postings WHERE journal_id='" + journal + "')")) {
            assertThat(result.next()).isTrue();
            for (int i = 1; i <= 3; i++) {
                assertThat(result.getInt(i)).isZero();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE dbo.postings SET amount=amount", "DELETE FROM dbo.postings", "UPDATE dbo.journals SET currency=currency",
            "DELETE FROM dbo.operations", "UPDATE dbo.operations SET amount=amount", "UPDATE dbo.accounts SET category=category",
            "INSERT dbo.accounts (account_id,category,currency) VALUES (NEWID(),'CUSTOMER_LIABILITY',N'IRR')",
            "CREATE TABLE dbo.cbs09_forbidden (id int)", "SELECT * FROM cbs_migrations.flyway_schema_history"})
    void runtimeCannotMutateHistoryIntentOrSchema(String sql) throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> MigrationSqlFixture.execute(connection, sql))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isIn(229, 230, 262));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void runtimeCanUpdateOnlyProjectionAndOutcomeColumnsAndIdentitiesAreNotAdministrators() throws Exception {
        try (var connection = runtime.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MigrationSqlFixture.execute(connection, "UPDATE dbo.accounts SET balance=balance WHERE account_id='" + MigrationSqlFixture.A + "'");
                var id = MigrationSqlFixture.operation(connection, Map.of());
                MigrationSqlFixture.execute(connection, "UPDATE dbo.operations SET state='REJECTED',rejection_reason='INSUFFICIENT_FUNDS',"
                        + "completed_at=DATEADD(second,1,SYSUTCDATETIME()),response_format_version=1,http_status=422,"
                        + "response_content_type='application/problem+json',response_body=0x7B7D WHERE operation_id='" + id + "'");
            } finally {
                connection.rollback();
            }
        }
        for (var database : new SqlServerTestDatabase[] {runtime, migrator, initializer}) {
            try (var connection = database.openConnection(); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT IS_SRVROLEMEMBER('sysadmin'), IS_MEMBER('db_owner')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
                assertThat(result.getInt(2)).isZero();
            }
        }
    }

    private static Flyway migration() {
        return Flyway.configure().dataSource(migrator.dataSource()).defaultSchema("cbs_migrations")
                .schemas("cbs_migrations").createSchemas(false).cleanDisabled(true).baselineOnMigrate(false)
                .locations("classpath:db/migration").validateMigrationNaming(true).load();
    }

    private static Arguments bad(String column, String value, String suffix) {
        return Arguments.of(Map.of(column, value), "ck_operations_" + suffix);
    }

    private static void seedZero(Connection connection, String id, String currency) throws SQLException {
        MigrationSqlFixture.execute(connection, "IF NOT EXISTS (SELECT 1 FROM dbo.accounts WHERE account_id='" + id + "') "
                + "INSERT dbo.accounts (account_id,category,currency) VALUES ('" + id + "','CUSTOMER_LIABILITY',N'" + currency + "')");
    }

    private static void expectConstraint(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String name) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SQLException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(547);
            assertThat(failure.getMessage()).contains(name);
        });
    }
}
