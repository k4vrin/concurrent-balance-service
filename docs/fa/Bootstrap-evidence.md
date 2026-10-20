# شواهد اعتبارسنجی اسکلت

تاریخ: ۲۰ اکتبر ۲۰۲۶، Asia/Tehran. [English](../en/Bootstrap-evidence.md)

- پروژهٔ `concurrent-balance-service` در مسیر نهایی محلی اعتبارسنجی شد.
- اجرا با Temurin Java 21+35 روی macOS arm64، Maven wrapper 3.10.0 و Spring Boot 4.1.1 انجام شد.
- فرمان `./mvnw -o -B -ntp clean verify` در مسیر نهایی موفق شد: یک تست HTTP health، بدون failure/error/skip و تولید JAR اجرایی. JAVA_HOME صریحاً JDK 21 بود.
- فرمان `./mvnw -B -ntp -Psqlserver,redis,kafka,security -DskipTests package` در staging موفق شد. dependencyهای اختیاری resolve/compile/package شدند؛ تست عمداً skip و سرویس خارجی بررسی نشد. هشدار 401 برای metadata برخی repositoryهای upstream مانع موفقیت نهایی نشد.
- لینک و fence مستندات، whitespace و XML بررسی شدند؛ لینک/fence در مسیر نهایی نیز موفق بود.
- این بررسی‌ها پیش از اولین commit عمومی انجام شدند. وضعیت CI گیت‌هاب در Actions مخزن گزارش می‌شود و از شواهد محلی جدا است.

مختصات اولیهٔ ناموجود parent و انتظار بیش‌ازحد دقیق JSON قبل از موفقیت اصلاح شدند. این تست‌ها صحت مالی، migration، اتصال دیتابیس، رفتار Redis/Kafka، سیاست JWT، OTP، CDC، FX یا throughput را ثابت نمی‌کنند. عدد کارایی نداریم.

معیار بعد: تکمیل تصمیم‌های P0، schema SQL Server و تست واقعی P1. خروجی build و لاگ generated و ignored هستند؛ فرمان‌ها قابل‌بازتولیدند.
