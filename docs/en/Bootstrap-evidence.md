# Bootstrap verification evidence

Date: 2026-10-20, Asia/Tehran. [فارسی](../fa/Bootstrap-evidence.md)

- Project: `concurrent-balance-service`, verified in its final local checkout.
- Runtime used: Eclipse Temurin Java 21+35, macOS arm64. Wrapper: Maven 3.10.0. Spring Boot parent: 4.1.1.
- Final-location `./mvnw -o -B -ntp clean verify`: BUILD SUCCESS, one HTTP health smoke test passed, zero failures/errors/skips; executable JAR packaged. JAVA_HOME explicitly selected Java 21.
- Staging `./mvnw -B -ntp -Psqlserver,redis,kafka,security -DskipTests package`: BUILD SUCCESS. This resolved/compiled/packaged optional libraries; tests were deliberately skipped and no external service was validated. Maven reported optional upstream metadata repository 401 warnings, but completed successfully using resolved artifacts.
- Local Markdown link/code-fence, whitespace and POM XML checks passed before handoff; final-location Markdown links and fences passed.
- These checks preceded the initial public commit. GitHub CI status is reported by the repository Actions page and is separate from this local evidence.

The initial unavailable parent coordinate and exact-JSON health expectation were corrected before the successful build. No financial operation, SQL migration, database connection, Redis/Kafka runtime, JWT authorization policy, OTP, CDC, FX integration or throughput claim is validated by these checks. No performance numbers are available.

Next gate: complete P0 transaction/constraint decisions, then add SQL Server schema and real-database P1 integration tests. Logs/target files are generated and ignored; reproduce with the commands above.
