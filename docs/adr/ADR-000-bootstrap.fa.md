# ADR-000: اسکلت و توسعه‌های مرحله‌ای

وضعیت: Accepted برای اسکلت اولیه. تاریخ: ۲۰ اکتبر ۲۰۲۶. [English](ADR-000-bootstrap.en.md)

Java 21، Maven wrapper، Spring Boot 4.1.1 و SQL Server هدف persistence باشند. کتابخانه‌های SQL Server، Redis، Kafka و JWT در profile صریح Maven قرار گیرند. اجرای پیش‌فرض health روی loopback و بدون سرویس خارجی است تا سلامت starter مستقل از زیرساخت بررسی شود.

profile Maven محتوای dependency و profile Spring تنظیم runtime را تعیین می‌کند؛ هر دو مستند باشند. dependency و config قابلیت مالی پیاده‌شده نیستند. Redis کپی نتیجهٔ ماندگار، Kafka غیرهم‌زمان، OTP وابسته به intent احرازشده و Debezium زیرساخت بیرونی مرحلهٔ بعد است. تصمیم مالی/schema در دفتر تصمیم‌ها Proposed مانده است.

هزینه: فرمان profile طولانی‌تر و هر classpath به gate جدا نیاز دارد. تست پیش‌فرض SQL Server یا classpath یکپارچه را تأیید نمی‌کند. CI کتابخانه‌ها را جدا compile می‌کند و تست واقعی همراه قابلیت می‌آید. H2 یا خاموش‌کردن migration نباید موفقیت تست SQL Server نامیده شود.

مختصات 4.1.1.RELEASE تولیدشدهٔ Initializr موجود نبود؛ به 4.1.1 استاندارد اصلاح و build بررسی می‌شود. جزئیات در Starter و شواهد واقعی پس از اجرا در سند evidence ثبت می‌شوند.
