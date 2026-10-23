package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Synthetic row-shape probes. Every operation/journal/posting write is rolled back by its caller. */
final class MigrationSqlFixture {
    static final String A = "cb509000-0000-4000-8000-000000000001";
    static final String B = "cb509000-0000-4000-8000-000000000002";
    static final String USD = "cb509000-0000-4000-8000-000000000003";

    private MigrationSqlFixture() {
    }

    static UUID operation(Connection connection, Map<String, String> overrides) throws SQLException {
        var id = UUID.randomUUID();
        var values = new LinkedHashMap<String, String>();
        values.put("operation_id", "'" + id + "'");
        values.put("transaction_id", "N'cbs09-" + id + "'");
        values.put("operation_type", "'TRANSFER'");
        values.put("source_account_id", "'" + A + "'");
        values.put("destination_account_id", "'" + B + "'");
        values.put("amount", "100");
        values.put("currency", "N'IRR'");
        values.put("canonical_format_version", "1");
        values.put("canonical_request", "0x0102");
        values.put("canonical_fingerprint", "HASHBYTES('SHA2_256', 0x0102)");
        values.put("execution_policy_version", "N'local-v1'");
        values.put("state", "'PENDING'");
        values.putAll(overrides);
        // SQL fragments come solely from these tests, never from requests/environment/credentials.
        execute(connection, "INSERT dbo.operations (" + String.join(",", values.keySet())
                + ") VALUES (" + String.join(",", values.values()) + ")");
        return id;
    }

    static Map<String, String> rejection() {
        return Map.of("state", "'REJECTED'", "rejection_reason", "'TRANSACTION_LIMIT_EXCEEDED'",
                "completed_at", "DATEADD(second, 1, SYSUTCDATETIME())", "response_format_version", "1",
                "http_status", "422", "response_content_type", "'application/problem+json'", "response_body", "0x7B7D");
    }

    static UUID journal(Connection connection, UUID operation) throws SQLException {
        var id = UUID.randomUUID();
        execute(connection, "INSERT dbo.journals (journal_id,operation_id,currency) VALUES ('"
                + id + "','" + operation + "',N'IRR')");
        return id;
    }

    static void posting(Connection connection, UUID journal, int line, String account, String currency,
            String side, long amount) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT dbo.postings VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, journal.toString());
            statement.setInt(2, line);
            statement.setString(3, account);
            statement.setNString(4, currency);
            statement.setString(5, side);
            statement.setLong(6, amount);
            statement.executeUpdate();
        }
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
