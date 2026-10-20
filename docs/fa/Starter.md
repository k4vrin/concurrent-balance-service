# راهنمای Spring starter و dependency

[English](../en/Starter.md)

## منشأ و نسخه

در ۲۰ اکتبر ۲۰۲۶ اسکلت از [Spring Initializr](https://start.spring.io/) با Java 21، Maven، گروه `dev.kavrin`، artifact برابر `concurrent-balance-service`، package برابر `dev.kavrin.balance` و JAR گرفته شد. شناسه‌های انتخابی `web,validation,actuator,data-jpa,flyway,sqlserver,data-redis,kafka,oauth2-resource-server` بودند.

Initializr نسخهٔ parent برابر `4.1.1.RELEASE` تولید کرد که در Maven Central حل نشد. مختصات استاندارد `org.springframework.boot:spring-boot-starter-parent:4.1.1` جایگزین شد؛ repository سفارشی نداریم. hash آرشیو اصلی در نسخهٔ انگلیسی ثبت شده است. wrapper، Maven 3.10.0 و enforcer 3.6.2 الزام JDK 21 را مشخص می‌کنند. نسخهٔ dependencyها را parent مدیریت می‌کند.

Boot 4 starterهای ماژولار مانند `spring-boot-starter-webmvc`، `spring-boot-starter-kafka`، `spring-boot-starter-flyway` و `spring-boot-starter-security-oauth2-resource-server` دارد. نام‌های Boot 3 از BankingLedger بدون تطبیق کپی نشوند. test starterهای تولیدشده کنار profile متناظر مانده‌اند.

## علت انتخاب

Web MVC سرور HTTP، Validation زیرساخت اعتبارسنجی و Actuator health اسکلت را می‌دهند. هیچ‌کدام منطق مالی نیستند. تست، سرور واقعی روی پورت تصادفی را با HTTP client خود JDK بررسی می‌کند.

profile Maven به نام `sqlserver`، JPA، Flyway، `org.flywaydb:flyway-sqlserver` و driver زمان اجرای `com.microsoft.sqlserver:mssql-jdbc` را اضافه می‌کند. به این ترتیب context پیش‌فرض نیازمند دیتابیس نیست. تنظیم SQL Server اعتبارنامهٔ محیطی و schema validation دارد؛ migration اول منتظر مرور صحت است و fallback به H2 نداریم.

`redis` دسترسی آینده به پاسخ نهایی و `kafka` ابزار producer/consumer مرحلهٔ outbox و تاریخچه را فراهم می‌کنند. تنظیمات نمونه به معنی پیاده‌سازی cache، event schema یا listener نیستند. idempotence تولیدکنندهٔ Kafka، بازنشر application outbox را حذف نمی‌کند و consumer همچنان dedup می‌خواهد.

`security` کتابخانه و تنظیم JWT است و سیاست نهایی دسترسی نیست. issuer واقعی و تست مالکیت/نقش پیش از endpoint مالی لازم‌اند. رمز توسعه یا OTP واقعی اضافه نشده است. Debezium زیرساخت connector جدا و عمداً خارج از dependency برنامه است. Testcontainers، OpenAPI و exporter مانیتورینگ در صورت نیاز مرحلهٔ مرتبط اضافه می‌شوند و فعلاً ادعا نمی‌شوند.

## ساختار و فرمان

مرز نخست feature در `operation/application/BalanceService` است. interface قرارداد بدون bean اجرایی است. در پیاده‌سازی آینده persistence حساب، تراکنش، confirmation، idempotency و event adapter مرز روشن داشته باشند؛ لایه‌های خالی متعدد فعلاً ایجاد نشوند.

`./mvnw verify` اسکلت پیش‌فرض را بررسی می‌کند. فرمان `./mvnw -Psqlserver,redis,kafka,security -DskipTests package` سازگاری dependency و compile کلاس‌پث کامل را بررسی می‌کند؛ اتصال زیرساخت یا تست persistence/خرابی نیست. معیارهای واقعی هر قابلیت هم‌زمان با آن اضافه شوند.

منابع: [Initializr](https://start.spring.io/)، [Spring Boot](https://docs.spring.io/spring-boot/)، [profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)، [Flyway SQL Server](https://documentation.red-gate.com/fd/sql-server-database-277579330.html). مرجع نسخهٔ دقیق POM پروژه است.
