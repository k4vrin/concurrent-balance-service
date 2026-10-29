# سرویس موجودی هم‌زمان

[![Build](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml/badge.svg)](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927)](https://www.microsoft.com/sql-server)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

سرویسی با Java 21 و Spring Boot برای credit، debit و انتقال ریال صحیح زیر درخواست‌های هم‌زمان. SQL Server موجودی مرجع، تاریخچهٔ حسابداری دوطرفه و پاسخ اصلی هر دستور مالی نهایی‌شده را کنار هم نگه می‌دارد.

اگر انتقال commit شود ولی پاسخ HTTP به فراخواننده نرسد، retry با همان `transactionId` پاسخ اصلی را برمی‌گرداند و پول را دوباره جابه‌جا نمی‌کند. اگر debit به‌خاطر کمبود موجودی رد شود، همان تصمیم بعد از افزایش موجودی هم replay می‌شود.

[English](README.md) · [اجرای نمونه‌های HTTP](docs/fa/HTTP-samples.md) · [مرجع API](docs/fa/API-v1.md) · [فهرست مستندات](docs/README.md)

## چه چیزی پیاده‌سازی شده است؟

**هستهٔ منتخب CBS20/G1 کامل شده است**: چهار عملیات HTTP، replay ماندگار موفقیت و رد، نوشتن اتمیک دفترکل، ترتیب مشترک قفل حساب‌ها، بازیابی محدود کل تراکنش و reconciliation مستقل. [راهنمای پذیرش](docs/fa/Core-acceptance.md)، نتیجهٔ **۴۲۱ تست قابل‌حمل + ۲۱۲ تست واقعی SQL Server** و شواهد rollback و restart در process جدا را ثبت می‌کند. [اجرای تأییدشدهٔ CI هسته](https://github.com/k4vrin/concurrent-balance-service/actions/runs/40886956745).

برنامه برای **اپراتور محلی مورداعتماد** است و به‌صورت پیش‌فرض روی `127.0.0.1:8080` گوش می‌دهد. پیش از دسترسی مشترک یا خارجی، احراز هویت، مالکیت حساب و مجوز عملیات تأمین لازم‌اند. benchmark کارایی، OTP، Redis، Kafka/outbox، CDC و FX در محدودهٔ بعدی قرار دارند.

| عملیات | endpoint | اثر |
| --- | --- | --- |
| Credit | `POST /api/v1/accounts/{accountId}/credits` | افزایش موجودی مشتری با حساب تأمین انتخاب‌شدهٔ سرور. |
| Debit | `POST /api/v1/accounts/{accountId}/debits` | کاهش موجودی مشتری در صورت کفایت موجودی. |
| Transfer | `POST /api/v1/transfers` | انتقال اتمیک میان دو حساب مشتری متمایز. |
| Balance | `GET /api/v1/accounts/{accountId}/balance` | خواندن موجودی و ارز commit‌شده. |

## شروع کار

پیش‌نیازها: **JDK 21**، Docker همراه Compose برای SQL Server و Bash برای فرمان‌های راه‌اندازی محلی. Maven wrapper در مخزن هست؛ فقط harness پذیرش خودکار هسته به Python 3 نیاز دارد. کانتینر SQL Server برای Linux x86-64 است؛ آزمایش مستندشدهٔ emulation روی ARM64 پشتیبانی Microsoft را ندارد.

```bash
git clone https://github.com/k4vrin/concurrent-balance-service.git
cd concurrent-balance-service
./mvnw verify
```

این فرمان تست‌های قابل‌حمل را بدون دیتابیس اجرا می‌کند. در Windows از `.\mvnw.cmd` استفاده کنید؛ مسیر جداگانهٔ راه‌اندازی در [راهنمای Windows و SQL Server](docs/fa/Windows-sqlserver.md) آمده است.

برای اجرای API واقعی، [راهنمای محلی HTTP](docs/fa/HTTP-samples.md) را دنبال کنید:

1. `.env.example` را در فایل نادیده‌گرفته‌شدهٔ `.env.sqlserver` کپی کنید، secretهای مجزا بگذارید و مجوز SQL Server Developer را بپذیرید.
2. کانتینر pin‌شدهٔ SQL Server را بالا بیاورید، identityهای جدا بسازید، `cbs_dev` و `cbs_test` را migrate کنید و grantهای runtime را اعمال کنید.
3. [fixture نمونه](http/create-demo-accounts.sql) را با initializer محدود اجرا کنید. فقط حساب با موجودی صفر می‌سازد و موجودی یا history را reset نمی‌کند.
4. برنامه را با credentialهای runtime، مقدار `EXECUTION_POLICY_ENABLED=true` و حساب funding مشخص راه‌اندازی کنید.
5. فایل [balance-service.http](http/balance-service.http) را در IntelliJ IDEA HTTP Client یا VS Code REST Client باز کنید و درخواست‌های شماره‌دار را اجرا کنید.

Spring Boot فایل `.env.sqlserver` را **خودکار نمی‌خواند**. راه‌اندازی عادی به `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` نیاز دارد؛ فرمان‌های دقیق در walkthrough آمده‌اند. تا وقتی execution غیرفعال است، mutationها پاسخ 503 می‌دهند. endpoint عمومی ساخت حساب نداریم.

## یک انتقال را امتحان کنید

پس از ساخت و شارژ حساب‌های نمونه طبق walkthrough، این درخواست را بفرستید:

```http
POST http://127.0.0.1:8080/api/v1/transfers
Content-Type: application/json

{
  "transactionId": "demo-v1-transfer",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "20000000-0000-4000-8000-000000000002",
  "amountRial": 250,
  "currency": "IRR"
}
```

انتقال commit‌شده با HTTP 200 پاسخ می‌دهد:

```json
{"transactionId":"demo-v1-transfer","operationType":"TRANSFER","currency":"IRR","status":"SUCCEEDED"}
```

همان body را دوباره بفرستید تا نتیجه replay شود. تغییر مبلغ با همان ID، پاسخ HTTP 409 با کد `TRANSACTION_ID_CONFLICT` دارد. فایل درخواست، رد کمبود موجودی پس از top-up، حساب ناموجود، self-transfer، مبلغ اعشاری و سقف تراکنش را هم با پاسخ موردانتظار نشان می‌دهد.

## قواعد مبلغ و سازگاری

- **هر واحد یک ریال است.** فیلدهای `amountRial` و `balanceRial` عدد صحیح‌اند؛ در Java از `long` و در SQL Server از `BIGINT` استفاده می‌شود. اعشار، token نمایی، رشتهٔ عددی و گردکردن بی‌صدا رد می‌شوند.
- **سیاست اولیه فقط IRR را می‌پذیرد.** سقف پیش‌فرض هر تراکنش و موجودی مشتری **۱۰٬۰۰۰٬۰۰۰٬۰۰۰٬۰۰۰ ریال** است. محاسبهٔ checked از overflow محدودهٔ نمایش هم جلوگیری می‌کند.
- **هویت درخواست سراسری است.** شناسه دقیق و حساس به بزرگی حروف است. retry منطبق پاسخ ذخیره‌شده را می‌گیرد؛ نوع یا payload متفاوت تعارض دارد.
- **اثر مالی یک‌جا commit می‌شود.** هر موفقیت، journal متوازن، postingها، projectionهای درگیر و پاسخ ماندگار را با هم commit می‌کند. رد کسب‌وکار فقط operation دارد و journal یا تغییر موجودی ندارد.
- **ترتیب قفل مشترک است.** همهٔ mutationها پیش از funds check، کل حساب‌های درگیر را با ترتیب unsigned descending UUID قفل می‌کنند. credit/debit شامل funding پیکربندی‌شده‌اند؛ transfer مسیر funding ندارد.
- **بازیابی محافظه‌کارانه است.** rollback قطعی و commit نامعلوم مسیر یکسان ندارند. پاسخ 503 یا گم‌شدن پاسخ، ثابت نمی‌کند که commit رخ نداده است؛ بازیابی را با همان ID انجام دهید.

## معماری

مرز سازگاری، یک برنامهٔ Spring Boot و یک SQL Server است. controllerها مستقیم handlerهای use case را صدا می‌زنند. handlerها محاسبهٔ دامنه و persistence را از طریق portهای application هماهنگ می‌کنند؛ coordinator مشترک، پایان تراکنش و recovery را مدیریت می‌کند.

```text
src/main/java/dev/kavrin/balance/
  account/      Account domain, balance query and account persistence
  ledger/       Journals, postings and projection calculations
  money/        Integer money representation and checked arithmetic
  operation/    Commands, handlers, identity, retry, HTTP and persistence adapters
src/main/resources/db/migration/  Reviewed Flyway schema
src/test/                        Portable and real SQL Server verification
http/                            Executable requests and zero-account demo fixture
scripts/                         Database setup and packaged-process acceptance
```

| جدول | مسئولیت |
| --- | --- |
| `accounts` | هویت، دسته و ارز حساب و projection هم‌زمان موجودی. |
| `operations` | درخواست canonical، هویت ماندگار، شواهد سیاست و پاسخ ذخیره‌شده. |
| `journals` | یک header حسابداری برای هر عملیات موفق. |
| `postings` | تاریخچهٔ immutable بدهکار/بستانکار برای بازسازی موجودی. |

identity برنامه نمی‌تواند schema بسازد یا تاریخچهٔ posting را تغییر/حذف کند. Flyway جداگانه با credential مهاجرت اجرا می‌شود؛ ساخت حساب صفر هم identity جدا دارد. [schema و مجوزها](docs/fa/Schema.md)، [دفترکل](docs/fa/Ledger.md) و [walkthrough معماری](docs/fa/Hexagonal-architecture.md) را ببینید.

## بررسی درستی

| فرمان | چه چیزی را بررسی می‌کند؟ | پیش‌نیاز |
| --- | --- | --- |
| `./mvnw verify` | تست‌های قابل‌حمل دامنه، application و مرز HTTP. | JDK 21. |
| `./mvnw -Psqlserver-it verify` | تست‌های قابل‌حمل و تست واقعی schema، مجوزها، atomicity، replay، هم‌زمانی و recovery در SQL Server. | `cbs_test` migrate‌شده و هر سه گروه credential صریح تست از [راهنمای schema](docs/fa/Schema.md). |
| `python3 scripts/verify-core.py --env-file .env.sqlserver` | suite کامل، سپس JVM مستقل برای rollback واقعی timeout قفل، retry همان ID، restart/replay و reconciliation مستقل JDBC. | JDK 21، Python 3 و محیط SQL Server پیکربندی‌شده. |

GitHub Actions بررسی قابل‌حمل، کامپایل با dependency profileهای اختیاری و پذیرش SQL Server موقت/artifact را اجرا می‌کند. نبود تنظیمات دیتابیس باعث شکست integration verification می‌شود و تست را بی‌صدا skip نمی‌کند. baseline ثبت‌شده، **۶۳۳ تست با صفر failure/error/skip** را گذرانده است؛ جزئیات و مرز شواهد در [پذیرش هسته](docs/fa/Core-acceptance.md) آمده‌اند. این زمان‌سنجی‌ها ادعای throughput یا آمادگی production نیستند.

## مستندات

از [نمونه‌های HTTP و راه‌اندازی محلی](docs/fa/HTTP-samples.md)، [قرارداد فعلی API](docs/fa/API-v1.md) و [فهرست مستندات](docs/README.md) شروع کنید. برای مرور طراحی، [معنای تراکنش و replay](docs/fa/Transaction-protocol.md)، [projection دفترکل](docs/fa/Journal-projection.md)، [شواهد recovery](docs/fa/Recovery-acceptance.md) و [پذیرش هسته](docs/fa/Core-acceptance.md) را ببینید. [نقشهٔ راه](docs/fa/Roadmap.md) هستهٔ تکمیل‌شده را از توسعه‌های بعدی جدا می‌کند؛ صفحه‌های شواهد تاریخ‌دار، checkpointهای تاریخی را نگه می‌دارند.

مشارکت در پروژه از [راهنمای مهندسی و Git](docs/fa/Workflow.md) و [AGENTS.md](AGENTS.md) پیروی می‌کند. مستندات فارسی و انگلیسی با هم نگه‌داری می‌شوند.

## مجوز

[MIT](LICENSE). SQL Server و dependencyهای دیگر مجوز خودشان را دارند. نسخهٔ Developer از SQL Server برای توسعه و تست و طبق مجوز مربوط به آن استفاده می‌شود.
