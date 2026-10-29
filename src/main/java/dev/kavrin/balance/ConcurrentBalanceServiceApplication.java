package dev.kavrin.balance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstraps the SQL Server-backed balance service and its feature components.
 *
 * <p>Normal startup requires explicit datasource settings and an already migrated schema. Optional
 * execution and infrastructure profiles are configured separately; the isolated smoke assembly lives
 * only in test sources.
 */
@SpringBootApplication
public class ConcurrentBalanceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConcurrentBalanceServiceApplication.class, args);
	}

}
