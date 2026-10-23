# SQL Server روی Windows بدون Docker

[English](../en/Windows-sqlserver.md)

نصب native روی Windows مسیر جایگزین اختیاری است. مسیر منتخب آموزش، [Docker Compose](Docker.md) است. این راهنما به‌معنی بررسی اتصال موفق روی Windows نیست.

## ۱. نسخه و ابزارها را انتخاب کنید

نسخهٔ پشتیبانی‌شدهٔ SQL Server Developer را با installer رسمی روی Windows سازگار نصب کنید. Database Engine Services و در صورت نیاز SSMS را جدا نصب کنید.

Developer برای توسعه و تست است. سرویس واقعی، حتی برای استفادهٔ داخلی، edition و license مناسب production می‌خواهد. نسخه، edition و compatibility level را پیش از انتخاب CDC ثبت کنید.

## ۲. دیتابیس و TCP را آماده کنید

- دیتابیس توسعهٔ خالی و اختصاصی `balance_service` بسازید.
- login برنامه را از `sa` جدا کنید.
- مجوز migration و runtime را هنگام طراحی schema جدا بررسی کنید.
- برای JDBC روی TCP، پورت واقعی و ترجیحاً ثابت تعیین کنید.

اتصال محلی SSMS، اتصال JDBC روی TCP را ثابت نمی‌کند.

## ۳. متغیرهای اتصال را تنظیم کنید

مقادیر محلی را در PowerShell قرار دهید و برنامه را اجرا کنید:

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=balance_service;encrypt=true;trustServerCertificate=false'
$env:DB_USERNAME = 'balance_app'
$env:DB_PASSWORD = '<set-locally>'
.\mvnw.cmd spring-boot:run
```

certificate باید برای client معتبر باشد. در محیط توسعهٔ ایزوله با certificate خودامضا، `trustServerCertificate=true` بررسی certificate را bypass می‌کند. این override محلی را به محیط مشترک یا production نبرید.

رمز واقعی را commit نکنید. برنامه `.env` را خودکار نمی‌خواند و login یا دیتابیس نمی‌سازد.

### اگر Java روی Mac اجرا می‌شود

در URL از hostname ویندوز استفاده کنید. `localhost` به دستگاه اجرای Java اشاره می‌کند؛ در این مثال همان Mac است.

نصب native دیتابیس، Redis، Kafka یا Kafka Connect را نصب نمی‌کند. این dependency‌ها جدا و در مراحل بعد آماده می‌شوند. اجرای کل stack روی Windows بدون ابزار اضافه تأیید نشده است.

## ۴. مرز بررسی را ثبت کنید

Flyway، Hibernate و driver مربوط به SQL Server به‌صورت پیش‌فرض حضور دارند و profile جدا نمی‌خواهند. هنوز migration مالی یا بررسی اتصال نداریم.

برای Debezium آینده، CDC بومی، SQL Server Agent و capture job، مجوزها، retention و سازگاری دقیق نسخه و edition را بررسی کنید. برای آزمایش CDC، Developer بر LocalDB یا Express ترجیح دارد. وجود همهٔ قابلیت‌ها در هر نسخهٔ رایگان را فرض نکنید.

منابع: [نصب Windows](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-installation-wizard-setup)، [ویرایش‌ها](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2025)، [JDBC encryption](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption)، [Debezium SQL Server](https://debezium.io/documentation/reference/stable/connectors/sqlserver.html).

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

مسیر Windows همان [دفترکل چهارجدولی](Ledger.md)، migration بررسی‌شده و integration test‌های مسیر Docker را می‌خواهد. موجودی غیرصفر بعد از مرور schema با journal افتتاحیه ساخته می‌شود. seed مستقیم موجودی، حسابداری را دور می‌زند. نصب Windows برای مسیر منتخب Docker لازم نیست.
