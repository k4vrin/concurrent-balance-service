# سرویس موجودی هم‌زمان

[مخزن عمومی](https://github.com/k4vrin/concurrent-balance-service) · [English](README.md)

این پروژه از چالش موجودی هم‌زمان مفید شروع شده و با Java 21، Spring Boot 4.1.1 و Maven پیش می‌رود. دیتابیس هدف SQL Server است.

## قرار است چه مسئله‌ای را حل کنیم؟

وقتی چند درخواست هم‌زمان موجودی یک حساب را تغییر می‌دهند، نتیجه باید درست بماند. اگر پاسخ انتقال گم شد و کلاینت retry کرد، نباید پول دوباره جابه‌جا شود. هستهٔ پروژه قرار است `credit`، `debit`، انتقال اتمیک و استعلام موجودی مرجع را پوشش دهد.

بعد از تکمیل هسته، تأیید انتقال، cache پاسخ در Redis، رویدادهای تاریخچه در Kafka و Debezium اضافه می‌شوند. اتصال به FX هنوز جهت طراحی آینده است و پیاده نشده است.

خروجی اولین نسخهٔ کاربردی، HTTP API با JSON و SQL Server خواهد بود. Docker Compose مسیر منتخب زیرساخت محلی است. interface جاوای فعلی فقط قرارداد داخلی bootstrap است؛ مرز تحویل در [قرارداد API](docs/fa/API-v1.md) آمده است.

API اولیه روی محیط توسعهٔ محلی اجرا می‌شود. JWT و کنترل مالکیت پیش از دسترسی اشتراکی یا خارجی اضافه می‌شوند.

## وضعیت فعلی

CBS05 با نوع تغییرناپذیر `Money(long amount, Currency currency)` شروع شده است. برای IRR، عدد 100 مستقیماً ۱۰۰ ریال است. Money صفر و مقدار علامت‌دار را می‌پذیرد؛ دستورها، postingها و نوع حساب قواعد علامت و سقف تجاری خود را کنترل خواهند کرد. Money جمع و تفریق هم‌ارز با کنترل overflow و مقایسه دارد. AccountCategory سیاست موجودی نامنفی مشتری و موجودی علامت‌دار تأمین مصنوعی را بررسی می‌کند. Account تغییرناپذیر و AccountId مبتنی بر UUID سیاست موجودی را هنگام ساخت و تغییر checked کنترل می‌کنند. AccountLockOrder ترتیب مشترک unsigned نزولی UUID را می‌دهد؛ قفل دیتابیس هنوز پیاده نشده است. Posting و Journal اکنون سطر مثبت، delta وابسته به نوع حساب و سند تک‌ارزی متوازن با جمع دقیق را بررسی می‌کنند. Journal.projectBalances اکنون snapshotهای نهایی را با جمع دقیق تغییر خالص آماده می‌کند. use case مالی و persistence هنوز پیاده نشده‌اند. CreditCommand، DebitCommand و TransferCommand هم شناسه‌های لازم، مبلغ مثبت و حساب‌های متمایز انتقال را بررسی می‌کنند. SupportedCurrencies عضویت ارز در مجموعهٔ ورودی تغییرناپذیر را می‌سنجد؛ اتصال تنظیمات یا دیتابیس هنوز در برنامه است. TransactionId هم هویت دقیق و حساس به بزرگی حروف را بدون trim اعتبارسنجی می‌کند. پایهٔ CBS23 اکنون چهار مسیر HTTP را با اعتبارسنجی سخت‌گیرانه و کد پایدار ProblemDetail عرضه می‌کند. مرز پیش‌فرض برنامه تا اتصال handler ماندگار پاسخ 503 می‌دهد؛ موفقیت مالی پیاده نشده است. [شواهد adapter](docs/fa/HTTP-adapter-evidence.md).

| وضعیت | جزئیات |
| --- | --- |
| پیاده‌شده | bootstrap رسمی Spring Initializr با مختصات قابل‌دریافت Maven، نقطهٔ شروع برنامه، interface چهار عملیات، تست HTTP سلامت مستقل از زیرساخت، dependency‌های پیش‌فرض SQL Server، profile‌های اختیاری Redis و Kafka و JWT، تنظیمات محیطی، تعریف CI و مستندات دوزبانه |
| هنوز پیاده نشده | منطق مالی، schema حساب‌ها، idempotency ماندگار، مالکیت و مجوز، OTP، رفتار cache، outbox، producer و consumer، CDC و تسویهٔ FX |
| هنوز بررسی نشده | آماده‌سازی و اتصال SQL Server، رفتار مالی و کارایی |

هنوز endpoint مالی نداریم. موفقیت تست bootstrap فقط سلامت اسکلت را نشان می‌دهد و صحت مالی را ثابت نمی‌کند.

## ساخت و اجرا

### تفاوت verify و اجرای برنامه

| مسیر | پیش‌نیاز و رفتار |
| --- | --- |
| `verify` | Java 21 و Maven wrapper؛ profile مخصوص تست `smoke`، auto-configuration مربوط به persistence را غیرفعال می‌کند |
| اجرای عادی | SQL Server در دسترس و متغیرهای `DB_URL`، `DB_USERNAME` و `DB_PASSWORD`؛ profile جدا برای SQL Server لازم نیست |

تست `smoke` اتصال دیتابیس را بررسی نمی‌کند.

### آماده‌سازی Java و اجرای فرمان‌ها

JDK 21 را نصب و `JAVA_HOME` را تنظیم کنید. build با major version دیگری از JDK رد می‌شود. wrapper نسخهٔ Maven را دریافت می‌کند؛ برای بررسی bootstrap به Maven سیستمی یا Docker نیاز نیست. اولین build برای دریافت dependency‌ها به اینترنت نیاز دارد.

پس از تنظیم دیتابیس، می‌توانید برنامه را اجرا و سلامت آن را بررسی کنید:

```sh
./mvnw verify
./mvnw spring-boot:run
curl http://127.0.0.1:8080/actuator/health
```

معادل فرمان‌ها در PowerShell:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

سرور فقط روی loopback گوش می‌دهد و مسیرهای health و info را در دسترس می‌گذارد. پاسخ سلامت شامل `"status":"UP"` است و ممکن است metadata دیگری هم داشته باشد.

SQL Server برای اجرای عادی لازم است؛ Redis و Kafka اختیاری‌اند. فایل `.env.example` مرجع متغیرهاست و Spring Boot فایل `.env` را خودکار نمی‌خواند.

## dependencyهای مرحله‌ای

Maven با `-P` انتخاب می‌کند چه dependency‌هایی وارد بسته شوند. profile زمان اجرای Spring تنظیمات آن‌ها را فعال می‌کند. برای Redis، Kafka و security این دو انتخاب باید هماهنگ باشند. SQL Server کتابخانه‌ها و تنظیم اتصال پیش‌فرض را دارد.

برای مثال، فعال‌کردن profile در Spring کتابخانهٔ غایب را اضافه نمی‌کند. فعال‌کردن صرف profile در Maven هم ممکن است auto-configuration را بدون تنظیمات لازم روشن کند.

| profile Maven و profile متناظر Spring | کتابخانه‌ها | کاربرد فعلی |
| --- | --- | --- |
| پیش‌فرض، بدون profile | Web MVC، Validation، Actuator، Data JPA، Flyway، ماژول SQL Server مربوط به Flyway، JDBC مایکروسافت و استارترهای تست متناظر | برنامهٔ مبتنی بر SQL Server، هنوز بدون schema کسب‌وکار |
| `redis` | Spring Data Redis و استارتر تست | cache پاسخ‌های نهایی در آینده |
| `kafka` | Spring Kafka و استارتر تست | انتشار outbox و مصرف رویدادهای تاریخچه در آینده |
| `security` | OAuth2 resource server و استارتر تست | اتصال به JWT در آینده؛ سیاست مالکیت و مجوز هنوز کامل نشده است |

فرمان زیر بسته‌بندی با همهٔ وابستگی‌های اختیاری را بررسی می‌کند و تست‌ها را اجرا نمی‌کند:

```sh
./mvnw -Predis,kafka,security -DskipTests package
```

این فرمان فقط سازگاری build و dependency‌ها را بررسی می‌کند. `verify` پیش‌فرض تست HTTP bootstrap را اجرا می‌کند. تست واقعی SQL Server و خرابی broker همراه مرحلهٔ مربوط اضافه می‌شود. H2 جای شواهد SQL Server را نمی‌گیرد.

پس از آماده‌سازی یک دیتابیس توسعهٔ خالی و تنظیم متغیرهای اتصال، فرمان اجرای برنامه چنین است:

```sh
./mvnw spring-boot:run
```

### مرز فعلی دیتابیس

هنوز migration مالی نداریم. Flyway ممکن است جدول history خودش را بسازد؛ این کار schema حساب‌ها را ایجاد نمی‌کند.

- `ddl-auto=validate` را حفظ کنید و constraint‌ها را پیش از V1 مرور کنید.
- JAR اجرایی هم همان متغیرهای اتصال را می‌خواند و profile SQL Server نمی‌خواهد.
- bootstrap را روی دیتابیس کسب‌وکار موجود آزمایش نکنید.

## شواهد اعتبارسنجی

در [گزارش bootstrap](docs/fa/Bootstrap-evidence.md)، اجرای پاک `verify` با Java 21 و موفقیت یک تست HTTP ثبت شده است. بسته‌بندی وابستگی‌های اختیاری هم در محیط موقت آماده‌سازی موفق بوده، اما تست‌های آن اجرا نشده‌اند. سرویس‌های خارجی بررسی نشده‌اند.

## مستندات

- [راهنمای جریان عملیات](docs/fa/Operation-flow.md) / [English](docs/en/Operation-flow.md)
- [نتیجهٔ کسب‌وکار و مرز commit](docs/fa/Operation-outcomes.md) / [English](docs/en/Operation-outcomes.md)

- [راهنمای Journal و محاسبهٔ مانده](docs/fa/Journal-projection.md) / [English](docs/en/Journal-projection.md)

- [بدهکار و بستانکار در انواع حساب](docs/fa/Debit-and-credit.md) / [English](docs/en/Debit-and-credit.md)

- [بیت‌های UUID و ترتیب قفل حساب](docs/fa/UUID-lock-order.md)

- [معماری پذیرفته‌شده و مثال‌ها](docs/fa/Architecture.md)

- [ماتریس نیازمندی و خطا](docs/fa/Requirements.md)
- [پروتکل هویت ماندگار](docs/fa/Idempotency-protocol.md)
- [پروتکل تراکنش و قفل](docs/fa/Transaction-protocol.md)

- [استارتر Spring و انتخاب وابستگی‌ها](docs/fa/Starter.md)
- [زیرساخت محلی با Docker Compose](docs/fa/Docker.md)
- [نصب بومی SQL Server روی Windows، مسیر جایگزین اختیاری](docs/fa/Windows-sqlserver.md)
- [نقشهٔ راه و معیارهای پذیرش](docs/fa/Roadmap.md)
- [دفتر تصمیم‌های معماری](docs/fa/Decisions.md)
- [مرور قیدها و هزینهٔ دیتابیس](docs/fa/Database-review.md)
- [plan اجرای واقعی و throughput](docs/fa/Query-performance.md)
- [گردش‌کار Git و مستندات دوزبانه](docs/fa/Workflow.md)
- [تصمیم پذیرفته‌شدهٔ bootstrap](docs/adr/ADR-000-bootstrap.fa.md)

اسناد مخزن مرجع برنامهٔ اجرا هستند و vault سابقهٔ بحث را نگه می‌دارد. ADR-007 طراحی دفترکل را پذیرفته، اما پروتکل‌های مالی هنوز به مرور نیاز دارند. overhead قیدها و throughput اندازه‌گیری نشده‌اند. گام بعد، پذیرش پروتکل تراکنش و قیدهای P1، نوشتن migration SQL Server و سپس پیاده‌سازی و تست هر چهار عملیات است.

## طراحی پذیرفته‌شدهٔ دفترکل V1

کاربر در ۲۰۲۶-۱۰-۲۲ گزینهٔ E را انتخاب کرد. V1 چهار جدول `accounts`، `operations`، `journals` و `postings` خواهد داشت. تاریخچه باید متوازن و تغییرناپذیر باشد و projection موجودی در همان تراکنش SQL Server با آن commit شود.

`credit` و `debit` حساب مقابل تأمین یا تسویه دارند. موجودی غیرصفر اولیه هم با journal افتتاحیه ثبت می‌شود. [طراحی دفترکل](docs/fa/Ledger.md) و [ADR-007](docs/adr/ADR-007-double-entry-ledger.fa.md) جزئیات این انتخاب را توضیح می‌دهند.

این طراحی پذیرفته شده، اما کد مالی، schema و شواهد مالی SQL Server هنوز وجود ندارند. مرور constraint‌ها و تراکنش، پیش‌نیاز migration مالی است.

محاسبهٔ خالص projection در `Journal.projectBalances` پیاده شد: جمع دقیق تغییرات و اعتبارسنجی همهٔ snapshotهای نهایی، بدون تغییر ورودی یا نوشتن در دیتابیس. verify با Java 21 تعداد ۹۲ تست را بدون خطا یا skip گذراند. شرح و مرزها در [طراحی دفترکل](docs/fa/Ledger.md) آمده است.
