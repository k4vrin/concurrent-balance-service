# سرویس موجودی هم‌زمان

[![Build](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml/badge.svg)](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927)](https://www.microsoft.com/sql-server)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[کلاس‌های credit و جریان درخواست](docs/fa/Credit-class-walkthrough.md)

سرویس Java و Spring Boot برای جابه‌جایی درست پول زیر درخواست‌های هم‌زمان؛ API موجودی مرجع، دفترکل دوطرفه و هویت ماندگار درخواست را در یک SQL Server کنار هم قرار می‌دهد.

[English](README.md) · [مرجع API](docs/fa/API-v1.md) · [معماری](docs/fa/Architecture.md) · [راه‌اندازی](docs/fa/Schema.md)

## نسخهٔ اول

نسخهٔ اول یک API محلی JSON برای افزایش موجودی، برداشت، انتقال اتمیک و استعلام موجودی است. این صفحه قرارداد نسخهٔ قابل‌تحویل را شرح می‌دهد. رفتار پیاده‌شده و شواهد نسخهٔ فعلی مخزن در [وضعیت پیاده‌سازی](docs/fa/Implementation-status.md) آمده است.

| عملیات | endpoint | مسئولیت |
| --- | --- | --- |
| Credit | `POST /api/v1/accounts/{accountId}/credits` | افزایش موجودی مشتری با حساب تأمین انتخاب‌شدهٔ سرور. |
| Debit | `POST /api/v1/accounts/{accountId}/debits` | کاهش موجودی مشتری در صورت کفایت موجودی. |
| Transfer | `POST /api/v1/transfers` | انتقال مبلغ میان دو حساب متمایز به‌صورت اتمیک. |
| Balance | `GET /api/v1/accounts/{accountId}/balance` | خواندن موجودی و ارز committed از مرجع اصلی. |

مبلغ، ریال صحیح است؛ هر واحد دقیقاً یک ریال است و با `long` در Java و `BIGINT` در SQL Server نمایش داده می‌شود. سیاست اولیه IRR را می‌پذیرد و سقف هر عملیات و موجودی مشتری **۱۰٬۰۰۰٬۰۰۰٬۰۰۰٬۰۰۰ ریال** است. مبلغ اعشاری، تبدیل ضمنی و گردکردن بی‌صدا رد می‌شوند.

## مدل درستی

هر mutation یک `transactionId` از فراخواننده دارد. retry منطبق، نتیجهٔ اصلی ذخیره‌شده را برمی‌گرداند؛ استفادهٔ دوباره با نوع یا payload متفاوت تعارض دارد. پاسخ گم‌شده با همان شناسه بازیابی می‌شود. رد نهایی کسب‌وکار هم نگه داشته می‌شود تا retry پس از تغییر موجودی یا سیاست دوباره آن را ارزیابی نکند.

هر mutation موفق، یک journal متوازن، postingهای تغییرناپذیر بدهکار و بستانکار، projection موجودی و نتیجه را در یک تراکنش commit می‌کند. رد journal ندارد و replay، posting تازه نمی‌سازد. موجودی اولیهٔ غیرصفر با journal افتتاحیهٔ داخلی ایجاد می‌شود.

همهٔ نویسنده‌ها پیش از بررسی موجودی، مجموعهٔ کامل حساب‌های درگیر را به ترتیب مشترک UUID در برنامه قفل می‌کنند. محاسبهٔ checked جلوی overflow را می‌گیرد. deadlock و timeout قفل، recovery محدود کل تراکنش می‌خواهند؛ commit نامطمئن با همان شناسه بازیابی می‌شود. [پروتکل تراکنش](docs/fa/Transaction-protocol.md).

| جدول | مسئولیت |
| --- | --- |
| `accounts` | هویت، دستهٔ حساب، ارز و projection هم‌زمان موجودی. |
| `operations` | هویت سراسری درخواست، intent از نوع canonical، شواهد سیاست و نتیجهٔ ذخیره‌شده. |
| `journals` | یک header حسابداری برای هر عملیات موفق. |
| `postings` | خط‌های تغییرناپذیر بدهکار و بستانکار برای بازسازی و reconciliation موجودی. |

## نمونهٔ API

درخواست انتقال در قرارداد نسخهٔ اول، UUID حساب و شناسهٔ دقیق حساس به حروف دارد:

```http
POST /api/v1/transfers
Content-Type: application/json

{
  "transactionId": "transfer-001",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "f0000000-0000-4000-8000-000000000002",
  "amountRial": 150000,
  "currency": "IRR"
}
```

mutation موفق، عملیات committed را تأیید می‌کند. خطا با `application/problem+json` و کد پایدار برنامه برمی‌گردد. [مرجع API](docs/fa/API-v1.md) parsing و نتیجه‌ها را تعریف می‌کند؛ [راهنمای عملیات](docs/fa/Operation-flow.md) مسیر کامل درخواست را توضیح می‌دهد.

## ساخت و راه‌اندازی محلی

پیش‌نیاز، JDK 21 و برای محیط integration، Docker است. Maven از wrapper تأمین می‌شود.

```bash
./mvnw verify
```

verify مستقل از زیرساخت دیتابیس نمی‌خواهد. طبق [راه‌اندازی SQL Server](docs/fa/Docker.md) و [راهنمای migration](docs/fa/Schema.md)، مجوز Developer مربوط را بپذیرید، هویت‌های جدا را بسازید و schema را اعمال کنید. secret از متغیر محیطی یا تنظیم محلی ignored می‌آید. runtime با DB_URL، DB_USERNAME و DB_PASSWORD محدود اجرا می‌شود و migration انجام نمی‌دهد.

```bash
./mvnw -Pdb-migrate flyway:migrate flyway:validate
./mvnw -Psqlserver-it verify
./mvnw spring-boot:run
```

فرمان migration و integration تنظیم محیطی جدا و صریح راهنما را می‌خواهند. در Windows از `.\mvnw.cmd` استفاده کنید. آزمایش محلی emulation روی ARM64 در SQL Server پشتیبانی رسمی Microsoft ندارد؛ [شواهد محیط](docs/fa/SQL-server-evidence.md) را ببینید.

## دامنه و توسعه‌های بعدی

نسخهٔ اول اپراتور محلی مورداعتماد را فرض می‌کند و روی loopback اجرا می‌شود. احراز هویت، مالکیت حساب و کنترل اختیار funding پیش از دسترسی اشتراکی یا خارجی لازم‌اند. ادعای آمادگی production یا throughput نداریم.

تأیید انتقال با OTP، cache نتیجه در Redis، رویداد Kafka/outbox، CDC با Debezium و تسویهٔ FX بعد از هسته می‌آیند. SQL Server مرجع مالی می‌ماند؛ cache و history غیرهم‌زمان تصمیم نمی‌گیرند پول دوباره جابه‌جا شود یا نه.

## راهنمای طراحی

- [دلیل جدایی canonical request و fingerprint](docs/fa/Canonical-design.md)
- [دفترکل دوطرفه و دستهٔ حساب](docs/fa/Ledger.md)
- [محاسبهٔ journal و projection موجودی](docs/fa/Journal-projection.md)
- [قید و مجوز دیتابیس](docs/fa/Schema.md)
- [نقشهٔ راه و شواهد پیاده‌سازی](docs/fa/Roadmap.md)
- [روند مشارکت و Git](docs/fa/Workflow.md)

## مجوز

[MIT](LICENSE). SQL Server و وابستگی‌های دیگر مجوز خودشان را حفظ می‌کنند.
