package dev.kavrin.balance.infrastructure.sqlserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SqlServerTestDatabaseTests {
    @Test
    void acceptsExplicitTestSettingsWithoutConnecting() {
        assertThatCode(() -> SqlServerTestDatabase.configured(settings(), new Properties())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SQLSERVER_TEST_URL", "SQLSERVER_TEST_USERNAME", "SQLSERVER_TEST_PASSWORD"})
    void requiresEveryDedicatedTestSetting(String key) {
        var environment = settings();
        environment.remove(key);
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(environment, new Properties()))
                .withMessageContaining(key);
    }

    @Test
    void doesNotFallBackToApplicationSettings() {
        var environment = Map.of("DB_URL", "jdbc:sqlserver://localhost;databaseName=cbs_dev", "DB_USERNAME", "app", "DB_PASSWORD", "test-only");
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(environment, new Properties()))
                .withMessageContaining("SQLSERVER_TEST_URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_PROFILES_ACTIVE", "SPRING_PROFILES_INCLUDE", "SPRING_PROFILES_DEFAULT"})
    void rejectsSmokeEnvironmentProfile(String key) {
        var environment = settings();
        environment.put(key, "local, smoke");
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(environment, new Properties()))
                .withMessageContaining("smoke");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.profiles.active", "spring.profiles.include", "spring.profiles.default"})
    void rejectsSmokeSystemProfile(String key) {
        var properties = new Properties();
        properties.setProperty(key, "local,smoke");
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(settings(), properties))
                .withMessageContaining("smoke");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:test", "jdbc:oracle:thin:@localhost"})
    void rejectsOtherDatabaseEngines(String url) {
        var environment = settings();
        environment.put("SQLSERVER_TEST_URL", url);
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(environment, new Properties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user=app", "password=hidden", "USERName=app", "accessToken=hidden"})
    void rejectsCredentialsEmbeddedInUrl(String property) {
        var environment = settings();
        environment.put("SQLSERVER_TEST_URL", environment.get("SQLSERVER_TEST_URL") + ";" + property);
        assertThatIllegalArgumentException().isThrownBy(() -> SqlServerTestDatabase.configured(environment, new Properties()))
                .withMessage("Keep JDBC credentials in dedicated test environment variables, not the URL");
    }

    private static Map<String, String> settings() {
        return new HashMap<>(Map.of(
                "SQLSERVER_TEST_URL", "jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true",
                "SQLSERVER_TEST_USERNAME", "cbs_app", "SQLSERVER_TEST_PASSWORD", "test-only-placeholder"));
    }
}
