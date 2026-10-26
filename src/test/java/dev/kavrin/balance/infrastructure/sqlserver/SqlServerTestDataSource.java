package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Uses only the explicitly validated test identity; per-call credential overrides are forbidden. */
final class SqlServerTestDataSource extends AbstractDataSource {
    private final SqlServerTestDatabase database;

    SqlServerTestDataSource(SqlServerTestDatabase database) { this.database = database; }

    @Override
    public Connection getConnection() throws SQLException { return database.openConnection(); }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Use the explicitly configured test identity");
    }
}
