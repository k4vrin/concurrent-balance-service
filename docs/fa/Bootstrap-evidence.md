شواهد تاریخی زیر مربوط به اسکلت مبتنی بر profile در ۲۰۲۶-۱۰-۲۰ است. در ۲۰۲۶-۱۰-۲۲ profileهای Maven و Spring مربوط به SQL Server حذف شدند؛ فرمان‌های فعلی در README است. اجرای عادی اکنون دیتابیس می‌خواهد و تست smoke، persistence را صریح کنار می‌گذارد.

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

## بررسی حذف profile در ۲۰۲۶-۱۰-۲۲

- JDK: JetBrains Runtime 21.0.11 روی macOS ARM64.
- فرمان `./mvnw -B -ntp clean verify` موفق شد؛ یک تست HTTP smoke، صفر failure/error/skip. تست از profile مخصوص تست smoke استفاده می‌کند و اتصال SQL Server یا رفتار مالی را ثابت نمی‌کند.
- build افزایشی اولیه موفق بود ولی YAML حذف‌شده در target مانده بود. clean build فایل قدیمی را حذف کرد. بررسی JAR حضور dependencyهای پیش‌فرض JPA/Flyway/JDBC مایکروسافت و نبود application-sqlserver.yaml و application-smoke.yaml مخصوص تست را تأیید کرد.
- اجرای عادی JAR با حذف overrideهای DB/Spring به‌علت DB_URL تنظیم‌نشده طبق انتظار شکست خورد. اتصال موفق یا آماده‌سازی دیتابیس انجام نشده است.
- اجرای اولیهٔ Maven در sandbox به cache دسترسی نداشت؛ بررسی بالا پس از مجازشدن دسترسی cache/شبکه انجام شد.
- تصمیم تنظیمات: [ADR-006](../adr/ADR-006-default-sqlserver.fa.md).

## اجرای bootstrap توسط یادگیرنده در ۲۰۲۶-۱۰-۲۲

یادگیرنده خروجی ترمینال فرمان‌های زیر را ارائه کرد؛ پایان اجرا ساعت ۱۶:۲۲:۳۳ به وقت تهران بود:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -B -ntp clean verify
```

Java 21.0.11 بررسی enforcer را گذراند. BUILD SUCCESS در ۶٫۸۸۹ ثانیه: یک تست HTTP smoke، صفر failure/error/skip؛ profile مربوط به smoke فعال و JAR اجرایی ساخته شد. درخواست metadata بعضی repositoryهای Flyway هشدار 401 و Mockito هشدار dynamic agent داشتند؛ مانع تکمیل نشدند. این فقط شاهد اجرای bootstrap ارائه‌شده توسط یادگیرنده است. اتصال SQL Server، migration و رفتار مالی بررسی نشده‌اند. توضیح CBS00، استدلال حالت تغییریافته و شواهد باقی‌ماندهٔ مرور Git/CI جدا ارزیابی شوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

کاربر در ۲۰۲۶-۱۰-۲۲ گزینهٔ E را در [ADR-007](../adr/ADR-007-double-entry-ledger.fa.md) پذیرفت. دامنهٔ V1 اکنون دفترکل چهارجدولی و projection هم‌زمان است. شمارش build قبلی فقط شاهد bootstrap باقی می‌ماند. این تغییر طراحی اثبات endpoint مالی، migration، صحت حسابداری، سازگاری SQL Server یا تسلط یادگیرنده نیست.

بررسی هم‌راستایی گزینهٔ E در ۲۰۲۶-۱۰-۲۲: دستور `./mvnw -B -ntp clean verify` با Java 21 و یک تست HTTP، صفر failure/error/skip در ۷٫۲۵۱ ثانیه موفق شد. تلاش sandbox اولیه به قفل cache Maven دسترسی نداشت و اجرای مجاز با دسترسی cache/loopback موفق شد. تغییر جاوا فقط مستند قرارداد است؛ تست حسابداری SQL Server اجرا نشد. validator مسیر یادگیری vault بدون خطا معتبر بود. هیچ کار یادگیرنده با این بررسی پذیرفته نشد.
