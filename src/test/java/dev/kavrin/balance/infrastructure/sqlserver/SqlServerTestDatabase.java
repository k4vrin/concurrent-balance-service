package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Explicit test-only JDBC boundary; never inherit the application's database credentials. */
final class SqlServerTestDatabase {
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(?i);\\s*(user|userName|password|accessToken)\\s*=");
    private final String url;
    private final Properties credentials;

    private SqlServerTestDatabase(String url, String username, String password) {
        this.url = url;
        this.credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
        credentials.setProperty("loginTimeout", "5");
        credentials.setProperty("socketTimeout", "5000");
    }

    static SqlServerTestDatabase fromEnvironment() {
        return configured(System.getenv(), System.getProperties());
    }

    static SqlServerTestDatabase fromEnvironment(String prefix) {
        var environment = new java.util.HashMap<>(System.getenv());
        for (var part : new String[] {"URL", "USERNAME", "PASSWORD"}) {
            environment.put("SQLSERVER_TEST_" + part, required(environment, prefix + "_" + part));
        }
        return configured(environment, System.getProperties());
    }

    javax.sql.DataSource dataSource() {
        return new org.springframework.jdbc.datasource.AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return openConnection();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Use the explicitly configured test identity");
            }
        };
    }

    static SqlServerTestDatabase configured(Map<String, String> environment, Properties properties) {
        for (var suffix : new String[] {"active", "include", "default"}) {
            rejectSmoke(properties.getProperty("spring.profiles." + suffix, ""));
            rejectSmoke(environment.getOrDefault("SPRING_PROFILES_" + suffix.toUpperCase(java.util.Locale.ROOT), ""));
        }
        var url = required(environment, "SQLSERVER_TEST_URL");
        if (!url.startsWith("jdbc:sqlserver://")) {
            throw new IllegalArgumentException("SQLSERVER_TEST_URL must identify SQL Server");
        }
        if (URL_CREDENTIALS.matcher(url).find()) {
            throw new IllegalArgumentException("Keep JDBC credentials in dedicated test environment variables, not the URL");
        }
        return new SqlServerTestDatabase(url, required(environment, "SQLSERVER_TEST_USERNAME"),
                required(environment, "SQLSERVER_TEST_PASSWORD"));
    }

    Connection openConnection() throws SQLException {
        var connection = DriverManager.getConnection(url, credentials);
        try {
            // Check the actual connected catalog before any fixture write, even if URL properties redirect it.
            if (!"cbs_test".equals(connection.getCatalog())
                    || !"Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName())) {
                throw new SQLException("Integration fixtures require the dedicated cbs_test SQL Server database");
            }
            return connection;
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static String required(Map<String, String> environment, String key) {
        var value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required; integration tests never skip for missing infrastructure");
        }
        return value;
    }

    private static void rejectSmoke(String profiles) {
        if (Arrays.stream(profiles.split("[,;\\s]+")).anyMatch("smoke"::equals)) {
            throw new IllegalArgumentException("The persistence-disabled smoke profile is forbidden for SQL Server integration tests");
        }
    }
}
