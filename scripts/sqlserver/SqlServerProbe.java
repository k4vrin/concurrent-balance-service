import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;

/** CBS07 connectivity probe only; it does not run Spring, migrations or financial transactions. */
class SqlServerProbe {
    public static void main(String[] args) throws Exception {
        var url = Objects.requireNonNull(System.getenv("DB_URL"), "DB_URL is required");
        var credentials = new Properties();
        credentials.setProperty("user", Objects.requireNonNull(System.getenv("DB_USERNAME"), "DB_USERNAME is required"));
        credentials.setProperty("password", Objects.requireNonNull(System.getenv("DB_PASSWORD"), "DB_PASSWORD is required"));
        credentials.setProperty("loginTimeout", "5");
        try (var connection = DriverManager.getConnection(url, credentials);
                var statement = connection.createStatement()) {
            var metadata = connection.getMetaData();
            System.out.println("driver=" + metadata.getDriverName() + " " + metadata.getDriverVersion());
            System.out.println("server=" + metadata.getDatabaseProductName() + " " + metadata.getDatabaseProductVersion());
            try (var result = statement.executeQuery("SELECT DB_NAME(), 1, IS_SRVROLEMEMBER('sysadmin')")) {
                if (!result.next() || result.getInt(2) != 1 || result.getInt(3) != 0) {
                    throw new IllegalStateException("Expected a connected non-administrator application login");
                }
                System.out.println("database=" + result.getString(1) + ", select=1, is_sysadmin=0");
            }
        }
    }
}
