# ADR-006: حضور SQL Server در برنامهٔ پیش‌فرض

وضعیت: تغییر تنظیمات پذیرفته‌شده به درخواست کاربر در ۲۰۲۶-۱۰-۲۲. [English](ADR-006-default-sqlserver.en.md)

این تصمیم فقط profileهای اختیاری dependency/runtime مربوط به SQL Server و اجرای عادی بدون زیرساخت در ADR-000 را جایگزین می‌کند. Java 21، Maven wrapper و مراحل اختیاری بعدی برقرارند. V1 یک HTTP API مبتنی بر SQL Server است؛ دو سوییچ جدا برای persistence الزامی پیچیدگی اضافی دارند.

JPA، Flyway، ماژول SQL Server، driver مایکروسافت و test starterهای متناظر dependency عادی Maven می‌شوند. تنظیم دیتابیس به application.yaml منتقل و application-sqlserver.yaml حذف می‌شود. اجرای عادی بدون profile به DB_URL، DB_USERNAME و DB_PASSWORD نیاز دارد. schema validation و migration فعال حفظ می‌شوند و migration کسب‌وکار اضافه نمی‌شود.

تست HTTP موجود، profile صریح smoke را فقط از src/test/resources فعال می‌کند. این profile، auto-configuration مربوط به JDBC، Hibernate، JPA repository و Flyway را کنار می‌گذارد و فقط اتصال اجزای HTTP و health را بررسی می‌کند. تست واقعی دیتابیس باید تنظیم datasource مستقل بدون این استثناها داشته و در نبود SQL Server شکست بخورد. fallback به H2 یا ادعای صحت persistence از تست smoke نتیجه نمی‌شود.

Redis، Kafka و security profileهای اختیاری Maven و Spring را حفظ می‌کنند. CI و فرمان‌های فعلی آرگومان profile مربوط به SQL Server را حذف می‌کنند. شواهد تاریخی با فرمان‌های اصلی و یادداشت جایگزینی حفظ می‌شوند. هزینه: اجرای عادی به محیط دیتابیس و سرور در دسترس نیاز دارد و تست مستقل از زیرساخت باید صریح جدا شود. بازگشت تنظیم نیازمند هماهنگی dependency، runtime، تست و مستندات است.

نتیجهٔ بررسی و محدودیت زیرساخت در [شواهد bootstrap](../fa/Bootstrap-evidence.md) ثبت می‌شوند.
