# راهنمای Spring starter و dependency‌ها

[English](../en/Starter.md)

## این starter چه چیزی را آماده می‌کند؟

قرار است بتوانیم bootstrap را بدون دیتابیس build و تست کنیم، اما اجرای عادی برنامه از همان ابتدا SQL Server بخواهد. برای مثال، موفقیت تست `smoke` به‌معنی اتصال موفق دیتابیس نیست. این جداسازی اجازه می‌دهد هر integration را در مرحلهٔ خودش بررسی کنیم.

## منشأ و نسخه‌ها

bootstrap را در ۲۰ اکتبر ۲۰۲۶ از [Spring Initializr](https://start.spring.io/) گرفتیم. انتخاب‌ها چنین بودند:

| گزینه | مقدار |
| --- | --- |
| Java | 21 |
| ابزار build | Maven |
| group | `dev.kavrin` |
| artifact | `concurrent-balance-service` |
| package | `dev.kavrin.balance` |
| خروجی | JAR |

شناسه‌های dependency انتخاب‌شده: `web,validation,actuator,data-jpa,flyway,sqlserver,data-redis,kafka,oauth2-resource-server`.

Initializr برای parent مقدار `4.1.1.RELEASE` تولید کرد که در Maven Central قابل‌دریافت نبود. آن را به مختصات استاندارد `org.springframework.boot:spring-boot-starter-parent:4.1.1` تغییر دادیم و repository سفارشی اضافه نکردیم. hash آرشیو اصلی در نسخهٔ انگلیسی ثبت شده است.

wrapper نسخهٔ Maven را روی 3.10.0 ثابت می‌کند و enforcer 3.6.2 اجرای JDK 21 را الزامی می‌کند. parent نسخهٔ dependency‌ها را مدیریت می‌کند.

Boot 4 از starter‌های ماژولار مثل `spring-boot-starter-webmvc`، `spring-boot-starter-kafka`، `spring-boot-starter-flyway` و `spring-boot-starter-security-oauth2-resource-server` استفاده می‌کند. مختصات Boot 3 را از BankingLedger بدون تطبیق کپی نکنید. test starter‌ها با dependency‌ها و profile‌های متناظر باقی مانده‌اند.

## علت انتخاب

| dependency | مسئولیت فعلی |
| --- | --- |
| Web MVC | سرور HTTP |
| Validation | زیرساخت اعتبارسنجی |
| Actuator | endpoint سلامت bootstrap |

این‌ها منطق مالی ندارند. smoke test سرور واقعی را روی پورت تصادفی بالا می‌آورد و با HTTP client خود JDK سلامت آن را بررسی می‌کند.

### SQL Server در مسیر پیش‌فرض

JPA، Flyway، `org.flywaydb:flyway-sqlserver` و runtime driver با مختصات `com.microsoft.sqlserver:mssql-jdbc`، dependency‌های عادی Maven هستند. تنظیم اتصال در `application.yaml` است و اجرای عادی profile جدا نمی‌خواهد.

تست HTTP، profile مخصوص تست `smoke` را فعال می‌کند. این profile، auto-configuration مربوط به datasource، Hibernate JPA، JPA repository‌ها و Flyway را غیرفعال می‌کند تا HTTP بدون دیتابیس تست شود.

اجرای عادی این بخش‌ها را فعال نگه می‌دارد و SQL Server در دسترس و `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` می‌خواهد. credential‌ها از محیط خوانده می‌شوند و schema validation فعال است. migration اول منتظر مرور صحت می‌ماند؛ H2 fallback نداریم. تست `smoke` صحت persistence را بررسی نمی‌کند.

### Redis و Kafka

profile به نام `redis` ابزار دسترسی به پاسخ‌های نهایی را برای آینده فراهم می‌کند. `kafka` کتابخانه‌های producer و consumer مرحلهٔ outbox و تاریخچه را اضافه می‌کند. تنظیم نمونه، پیاده‌سازی cache، event schema یا listener نیست.

idempotence در Kafka producer جلوی بازنشر outbox توسط برنامه را نمی‌گیرد. consumer همچنان به dedup نیاز دارد.

### امنیت و ابزارهای مراحل بعد

profile به نام `security` کتابخانه و تنظیم JWT را فراهم می‌کند، اما سیاست کامل دسترسی نیست. پیش از در دسترس قرارگرفتن endpoint‌های مالی، issuer واقعی و تست‌های مالکیت و نقشِ مرحلهٔ مربوط لازم‌اند. رمز توسعه یا OTP واقعی اضافه نشده است.

Debezium به connector خارجی نیاز دارد و عمداً dependency Maven برنامه نیست. Testcontainers، OpenAPI و exporter‌های پایش در صورت نیاز مرحلهٔ مربوط اضافه می‌شوند؛ فعلاً جزو قابلیت‌های موجود نیستند.

## ساختار و فرمان

ساختار feature-based از `operation/application/BalanceService` شروع می‌شود. این interface قرارداد داخلی بدون bean اجرایی است و معیار تحویل V1 نیست. خروجی V1 در [قرارداد API](API-v1.md) مشخص است و امضاهای داخلی می‌توانند برای نتیجه‌های صریح تغییر کنند.

در پیاده‌سازی، persistence حساب، هماهنگی تراکنش، confirmation، idempotency و event adapter‌ها باید مرز روشن داشته باشند. فعلاً لایه‌های خالی متعدد نسازید.

| فرمان | چه چیزی را بررسی می‌کند؟ |
| --- | --- |
| `./mvnw verify` | bootstrap پیش‌فرض |
| `./mvnw -Predis,kafka,security -DskipTests package` | سازگاری dependency‌ها و compile با classpath کامل مرحله‌ای؛ تست‌ها اجرا نمی‌شوند |

فرمان package اتصال زیرساخت یا تست persistence و خرابی نیست. معیار واقعی هر قابلیت همراه همان قابلیت اضافه می‌شود.

منابع: [Initializr](https://start.spring.io/)، [Spring Boot](https://docs.spring.io/spring-boot/)، [profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)، [Flyway SQL Server](https://documentation.red-gate.com/fd/sql-server-database-277579330.html). مرجع نسخهٔ دقیق POM پروژه است.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

[طراحی دفترکل](Ledger.md) مسئولیت projection حساب، هویت عملیات و ساخت و ثبت journal را مشخص می‌کند. مرزهای feature را هنگام پیاده‌سازی حفظ کنید؛ اکنون package خالی یا سرویس دوم نسازید. `BalanceService` همچنان قرارداد داخلی بدون پیاده‌سازی است. قواعد دامنه در CBS05 و persistence بررسی‌شده در CBS09–10 اضافه می‌شوند؛ کد مالی موجودی برای جایگزینی نداریم.
