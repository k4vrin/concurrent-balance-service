# ADR-006: حضور SQL Server در برنامهٔ پیش‌فرض

وضعیت: تغییر تنظیمات پذیرفته‌شده به درخواست کاربر در ۲۰۲۶-۱۰-۲۲. [English](ADR-006-default-sqlserver.en.md)

## چرا profile جدا را حذف می‌کنیم؟

V1 یک HTTP API روی SQL Server است. وقتی persistence الزامی است، فعال‌کردن جداگانهٔ dependency در Maven و runtime در Spring فقط تنظیم اضافی می‌سازد. اجرای عادی باید بدون این دو switch از SQL Server استفاده کند.

این ADR فقط profile‌های اختیاری SQL Server و startup بدون زیرساخت در ADR-000 را جایگزین می‌کند. Java 21، wrapper و مراحل اختیاری بعدی حفظ می‌شوند.

## تغییرها

| بخش | تصمیم |
| --- | --- |
| Maven | JPA، Flyway، ماژول SQL Server، Microsoft JDBC driver و test starter‌های متناظر dependency عادی می‌شوند |
| تنظیمات | تنظیم دیتابیس به `application.yaml` می‌رود و `application-sqlserver.yaml` حذف می‌شود |
| startup | بدون profile جدا، `DB_URL` و `DB_USERNAME` و `DB_PASSWORD` لازم‌اند |
| schema | validation و migration فعال می‌مانند؛ migration کسب‌وکار اضافه نمی‌شود |

## تست مستقل از زیرساخت

HTTP smoke test، profile صریح `smoke` را فقط از `src/test/resources` فعال می‌کند. این profile، auto-configuration مربوط به JDBC، Hibernate، JPA repository و Flyway را غیرفعال می‌کند. تست فقط HTTP wiring و health را می‌بیند.

مثلاً `verify` موفق بدون دیتابیس، اتصال SQL Server را ثابت نمی‌کند. database integration test باید datasource مستقل بدون این exclusion‌ها داشته باشد و در نبود SQL Server fail شود. H2 fallback نداریم.

## اثر روی CI و شواهد

Redis، Kafka و `security` profile‌های اختیاری Maven و Spring را نگه می‌دارند. آرگومان profile مربوط به SQL Server از CI و فرمان‌های فعلی حذف می‌شود. فرمان تاریخی با یادداشت supersession حفظ می‌شود.

## هزینه و بازگشت تنظیم

startup عادی اکنون تنظیمات دیتابیس و SQL Server در دسترس می‌خواهد. تست portable باید صریحاً از زیرساخت جدا شود. بازگرداندن تنظیم، تغییر هماهنگ dependency، runtime، تست و مستندات می‌خواهد.

نتیجهٔ بررسی و محدودیت زیرساخت در [شواهد bootstrap](../fa/Bootstrap-evidence.md) ثبت می‌شوند.
