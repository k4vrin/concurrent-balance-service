# سرویس موجودی هم‌زمان

[مخزن عمومی](https://github.com/k4vrin/concurrent-balance-service)
[English](README.md)

Java 21، Spring Boot 4.1.1، Maven و هدف persistence با SQL Server.

این پروژه از چالش موجودی مفید شروع می‌شود. هستهٔ آینده شامل credit، debit، انتقال اتمیک و خواندن موجودی مرجع است. تأیید انتقال، Redis، تاریخچهٔ Kafka و Debezium در مراحل بعد می‌آیند. استفادهٔ FX جهت طراحی آینده است و هنوز پیاده‌سازی نشده است.

اولین نسخهٔ کاربردی، credit، debit، transfer و استعلام موجودی را با HTTP API و JSON، SQL Server و زیرساخت Docker Compose ارائه می‌کند. interface جاوا اسکلت داخلی است. [قرارداد API نسخهٔ اول](docs/fa/API-v1.md) را ببینید. JWT و مالکیت پیش از دسترسی اشتراکی یا خارجی اضافه می‌شوند؛ API اولیه فقط برای توسعهٔ محلی است.

## وضعیت فعلی

موجود: اسکلت رسمی Spring Initializr با مختصات قابل‌حل Maven، نقطهٔ شروع برنامه، interface چهار عملیات، تست HTTP health بدون زیرساخت، dependencyهای پیش‌فرض SQL Server و profileهای اختیاری Redis/Kafka/JWT، تنظیمات محیطی، تعریف CI و مستندات دوزبانه.

ناموجود: منطق مالی، schema حساب، idempotency ماندگار، سیاست مالکیت و دسترسی، OTP، رفتار cache، outbox، producer/consumer، CDC و تسویهٔ FX. endpoint مالی نداریم. SQL Server نصب یا اتصال آن بررسی نشده است. تست bootstrap اثبات صحت مالی نیست.

## اجرا

اجرای عادی برنامه به SQL Server در دسترس و متغیرهای `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` نیاز دارد؛ profile برای SQL Server وجود ندارد. فرمان `verify` با profile مخصوص تست به نام `smoke`، auto-configuration مربوط به persistence را کنار می‌گذارد و اتصال دیتابیس را تست نمی‌کند.

JDK 21 نصب و `JAVA_HOME` تنظیم شود؛ build نسخهٔ major دیگر را رد می‌کند. wrapper، Maven را می‌گیرد و برای bootstrap به Docker یا Maven سیستمی نیاز نیست. اولین build برای دانلود dependency اینترنت می‌خواهد.

```sh
./mvnw verify
./mvnw spring-boot:run
curl http://127.0.0.1:8080/actuator/health
```

در PowerShell:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

برنامه به‌طور پیش‌فرض فقط روی loopback گوش می‌دهد و health/info را ارائه می‌کند. پاسخ health شامل `"status":"UP"` است و ممکن است metadata اضافه داشته باشد. اجرای عادی به SQL Server نیاز دارد؛ Redis و Kafka اختیاری‌اند. مثال‌های startup و curl بالا پس از تنظیم دیتابیس اجرا شوند. `.env.example` مرجع متغیرهاست؛ Spring Boot فایل `.env` را خودکار نمی‌خواند.

## dependencyهای مرحله‌ای

Maven با `-P` کتابخانه‌های داخل build را انتخاب می‌کند و profile زمان اجرای Spring تنظیمات را فعال می‌کند. برای Redis، Kafka و security هر دو باید هماهنگ باشند. SQL Server dependency و تنظیم اتصال پیش‌فرض برنامه است. profile زمان اجرا dependency اضافه نمی‌کند و profile dependency تنها، می‌تواند auto-configuration بدون تنظیم لازم را روشن کند.

| profile Maven و Spring متناظر | dependency | کاربرد فعلی |
| --- | --- | --- |
| پیش‌فرض بدون profile | Web MVC، Validation، Actuator، JPA، Flyway، ماژول SQL Server، JDBC مایکروسافت و test starterها | برنامهٔ SQL Server؛ هنوز بدون schema کسب‌وکار |
| `redis` | Data Redis و test starter | cache پاسخ نهایی آینده |
| `kafka` | Kafka و test starter | publisher و consumer آینده |
| `security` | OAuth2 resource server و test starter | JWT آینده؛ سیاست نهایی مالکیت/دسترسی نداریم |

بررسی build تمام dependencyها، بدون ادعای تست integration:

```sh
./mvnw -Predis,kafka,security -DskipTests package
```

`verify` پیش‌فرض تست HTTP اسکلت را اجرا می‌کند. تست واقعی SQL Server و خرابی broker در مراحل خود اضافه می‌شوند؛ H2 جای شواهد SQL Server نیست.

پس از آماده‌سازی دیتابیس توسعهٔ خالی و تنظیم `DB_URL`، `DB_USERNAME` و `DB_PASSWORD`:

```sh
./mvnw spring-boot:run
```

migration مالی نداریم؛ Flyway ممکن است جدول تاریخچهٔ خودش را بسازد و این به معنی ایجاد حساب نیست. `ddl-auto=validate` حفظ و constraintها پیش از V1 مرور شوند. JAR ساخته‌شده همان متغیرهای محیطی را بدون profile مربوط به SQL Server می‌گیرد. آزمایش bootstrap روی دیتابیس کسب‌وکار موجود انجام نشود.

## شواهد بررسی

[شواهد bootstrap](docs/fa/Bootstrap-evidence.md): clean verify با Java 21 و یک تست HTTP موفق شد. build dependencyهای اختیاری در staging با skip تست موفق بود. سرویس‌های خارجی بررسی نشده‌اند.

## مستندات

- [Spring starter و dependencyها](docs/fa/Starter.md)
- [زیرساخت محلی با Docker Compose](docs/fa/Docker.md)
- [جایگزین اختیاری SQL Server بومی روی Windows](docs/fa/Windows-sqlserver.md)
- [نقشهٔ راه](docs/fa/Roadmap.md)
- [دفتر تصمیم‌های معماری و OTP](docs/fa/Decisions.md)
- [constraint و هزینهٔ دیتابیس](docs/fa/Database-review.md)
- [پلن واقعی و throughput](docs/fa/Query-performance.md)
- [گردش‌کار Git و مستندات](docs/fa/Workflow.md)
- [تصمیم پذیرفته‌شدهٔ bootstrap](docs/adr/ADR-000-bootstrap.fa.md)

اسناد این مخزن مرجع برنامهٔ اجرای بعدی هستند و vault سابقهٔ بحث را نگه می‌دارد. ADRهای مالی پیشنهادی‌اند. هیچ عدد overhead یا throughput اندازه‌گیری نشده است. قدم بعد پذیرش پروتکل تراکنش و constraintهای P1، migration SQL Server و سپس پیاده‌سازی/آزمایش هر چهار عملیات است.
