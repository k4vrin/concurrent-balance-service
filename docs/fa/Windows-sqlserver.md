# SQL Server روی Windows بدون Docker

[English](../en/Windows-sqlserver.md)

نسخهٔ Developer پشتیبانی‌شدهٔ SQL Server با installer رسمی روی Windows سازگار نصب شود. Database Engine Services و در صورت نیاز SSMS جدا نصب شوند. Developer برای توسعه/تست است؛ سرویس داخلی واقعی edition/license مجاز برای production می‌خواهد. نسخه، edition و compatibility level قبل از انتخاب CDC ثبت شوند.

دیتابیس توسعهٔ خالی و اختصاصی `balance_service` و login برنامهٔ جدا از `sa` بسازید. هنگام طراحی schema، مجوز migration از runtime جدا بررسی شود. برای JDBC روی TCP پورت واقعی و ترجیحاً ثابت تنظیم شود؛ اتصال محلی SSMS به‌تنهایی اتصال JDBC TCP را ثابت نمی‌کند.

نمونهٔ PowerShell با مقدار محلی:

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=balance_service;encrypt=true;trustServerCertificate=false'
$env:DB_USERNAME = 'balance_app'
$env:DB_PASSWORD = '<set-locally>'
.\mvnw.cmd -Psqlserver spring-boot:run '-Dspring-boot.run.profiles=sqlserver'
```

certificate معتبر برای client استفاده شود. برای instance توسعهٔ ایزوله با certificate خودامضا، override محلی `trustServerCertificate=true` بررسی certificate را دور می‌زند؛ به تنظیم مشترک/production منتقل نشود. رمز واقعی commit نشود. پروژه `.env` را خودکار نمی‌خواند و login/database نمی‌سازد.

اگر Java روی دستگاه دیگر از جمله Mac فعلی اجرا شود، hostname ویندوز در URL بیاید؛ `localhost` دستگاه اجرای Java است. نصب بومی SQL Server به معنی نصب Redis، Kafka یا Kafka Connect نیست. این وابستگی‌ها در مراحل بعد جدا آماده می‌شوند؛ اجرای کل stack بدون ابزار اضافه روی Windows ادعا نمی‌شود.

Flyway و Hibernate پشت profile مربوط‌اند، ولی migration مالی و بررسی اتصال نداریم. معیار Debezium آینده باید CDC بومی، SQL Server Agent/capture job، مجوز، retention و edition/version سازگار را بررسی کند. برای آزمایش CDC نسخهٔ Developer به LocalDB/Express ترجیح دارد؛ وجود همهٔ featureها در هر نسخهٔ رایگان فرض نشود.

منابع: [نصب Windows](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-installation-wizard-setup)، [editionها](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2025)، [JDBC encryption](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption)، [Debezium SQL Server](https://debezium.io/documentation/reference/stable/connectors/sqlserver.html).
