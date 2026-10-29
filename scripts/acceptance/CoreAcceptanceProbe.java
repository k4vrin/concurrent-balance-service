import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Independently seeds and inspects the dedicated core-acceptance SQL Server fixtures over JDBC.
 *
 * <p>Only zero accounts are inserted through the initializer; financial effects must originate
 * from the packaged application's HTTP API. Runtime checks require cbs_test and a non-owner,
 * non-administrator login. The hold action keeps a real no-op account update uncommitted until
 * stdin signals release. No schema, grants or existing history is altered or deleted.
 */
class CoreAcceptanceProbe {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { throw new IllegalArgumentException("Expected seed, hold, absent or check"); }
        String prefix = args[0].equals("seed") ? "SQLSERVER_INITIALIZER_TEST" : "SQLSERVER_TEST";
        try (var connection = open(prefix)) {
            switch (args[0]) {
                case "seed" -> {
                    for (int i = 1; i <= 3; i++) {
                        try (var statement = connection.prepareStatement("INSERT dbo.accounts(account_id,category,currency) VALUES(CAST(? AS uniqueidentifier),?,N'IRR')")) {
                            statement.setString(1, args[i]);
                            statement.setString(2, i == 1 ? "FUNDING_CLEARING" : "CUSTOMER_LIABILITY");
                            require(statement.executeUpdate() == 1, "Account insertion failed");
                        }
                    }
                    System.out.println("SEED_OK");
                }
                case "hold" -> {
                    connection.setAutoCommit(false);
                    try {
                        try (var statement = connection.prepareStatement("UPDATE dbo.accounts SET balance=balance WHERE account_id=CAST(? AS uniqueidentifier)")) {
                            statement.setString(1, args[1]);
                            require(statement.executeUpdate() == 1, "Missing held account");
                        }
                        System.out.println("LOCKED");
                        System.out.flush();
                        System.in.read();
                    } finally { connection.rollback(); }
                }
                case "absent" -> {
                    try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM dbo.operations WHERE transaction_id=?")) {
                        statement.setString(1, args[1]);
                        try (var rows = statement.executeQuery()) { rows.next(); require(rows.getInt(1) == 0, "Unexpected retained operation"); }
                    }
                    System.out.println("ABSENT_OK");
                }
                case "check" -> check(connection, args);
                default -> throw new IllegalArgumentException("Unknown acceptance action");
            }
        }
    }

    private static Connection open(String prefix) throws SQLException {
        String url = required(prefix + "_URL");
        require(url.startsWith("jdbc:sqlserver://") && !url.matches("(?is).*;\\s*(user|username|password|accessToken)\\s*=.*"), "Use a credential-free SQL Server test URL");
        var credentials = new Properties();
        credentials.setProperty("user", required(prefix + "_USERNAME"));
        credentials.setProperty("password", required(prefix + "_PASSWORD"));
        credentials.setProperty("loginTimeout", "5");
        credentials.setProperty("socketTimeout", "5000");
        var connection = DriverManager.getConnection(url, credentials);
        try {
            require("cbs_test".equals(connection.getCatalog()), "Acceptance requires actual cbs_test catalog");
            require("Microsoft SQL Server".equals(connection.getMetaData().getDatabaseProductName()), "Acceptance requires actual SQL Server");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT IS_SRVROLEMEMBER('sysadmin'),IS_MEMBER('db_owner')")) {
                rows.next(); require(rows.getInt(1) == 0 && rows.getInt(2) == 0, "Restricted identities are required");
            }
            return connection;
        } catch (Throwable failure) { connection.close(); throw failure; }
    }

    private static void check(Connection connection, String[] args) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*),SUM(CASE WHEN state='SUCCEEDED' THEN 1 ELSE 0 END),
                    SUM(CASE WHEN state='REJECTED' THEN 1 ELSE 0 END),SUM(CASE WHEN state='PENDING' THEN 1 ELSE 0 END)
                FROM dbo.operations WHERE transaction_id LIKE ?
                """)) {
            statement.setString(1, args[1] + "%");
            try (var rows = statement.executeQuery()) {
                rows.next();
                require(rows.getInt(1) == 7 && rows.getInt(2) == 6 && rows.getInt(3) == 1 && rows.getInt(4) == 0,
                        "Expected seven terminal operations, six successes and one rejection");
            }
        }
        try (var statement = connection.prepareStatement("""
                SELECT o.state,COUNT(DISTINCT j.journal_id),COUNT(p.line_number),
                    COALESCE(SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0)) ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                FROM dbo.operations o LEFT JOIN dbo.journals j ON j.operation_id=o.operation_id
                LEFT JOIN dbo.postings p ON p.journal_id=j.journal_id
                WHERE o.transaction_id LIKE ? GROUP BY o.operation_id,o.state
                """)) {
            statement.setString(1, args[1] + "%");
            try (var rows = statement.executeQuery()) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    boolean success = rows.getString(1).equals("SUCCEEDED");
                    require(rows.getInt(2) == (success ? 1 : 0) && rows.getInt(3) == (success ? 2 : 0)
                            && rows.getBigDecimal(4).signum() == 0, "History completeness or balance mismatch");
                }
                require(count == 7, "Missing history result");
            }
        }
        for (int i = 2; i <= 4; i++) {
            try (var statement = connection.prepareStatement("""
                    SELECT a.balance,COALESCE(SUM(CASE
                        WHEN p.side=CASE WHEN a.category='FUNDING_CLEARING' THEN 'DEBIT' ELSE 'CREDIT' END
                        THEN CAST(p.amount AS decimal(38,0)) ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                    FROM dbo.accounts a LEFT JOIN dbo.postings p ON p.account_id=a.account_id
                    WHERE a.account_id=CAST(? AS uniqueidentifier) GROUP BY a.balance,a.category
                    """)) {
                statement.setQueryTimeout(3);
                statement.setString(1, args[i]);
                try (var rows = statement.executeQuery()) {
                    require(rows.next(), "Missing acceptance account");
                    long expected = i == 2 ? 1500 : i == 3 ? 725 : 775;
                    require(rows.getLong(1) == expected && rows.getBigDecimal(2).compareTo(java.math.BigDecimal.valueOf(expected)) == 0,
                            "Projection/history reconstruction mismatch");
                }
            }
        }
        System.out.println("CHECK_OK operations=7 successes=6 rejected=1 journals=6 postings=12 balances=725,775 funding=1500");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        require(value != null && !value.isBlank(), name + " is required");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
