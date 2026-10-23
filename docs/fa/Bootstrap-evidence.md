این گزارش اجرای تاریخی bootstrap را ثبت می‌کند. بخش ۲۰۲۶-۱۰-۲۰ مربوط به ساختار قدیمیِ profileهاست. در ۲۰۲۶-۱۰-۲۲ profile‌های Maven و Spring مربوط به SQL Server حذف شدند؛ فرمان فعلی در README است.

اجرای عادی اکنون دیتابیس می‌خواهد و تست `smoke`، persistence را صریحاً غیرفعال می‌کند. فرمان‌های تاریخی را به‌عنوان فرمان فعلی کپی نکنید.

# شواهد بررسی bootstrap

تاریخ: ۲۰ اکتبر ۲۰۲۶، Asia/Tehran. [English](../en/Bootstrap-evidence.md)

## اجرای اولیه در مسیر نهایی

| مشخصه | مقدار ثبت‌شده |
| --- | --- |
| پروژه | `concurrent-balance-service` در checkout نهایی محلی |
| JDK | Temurin Java 21+35 |
| میزبان | macOS arm64 |
| wrapper | Maven 3.10.0 |
| Spring Boot | 4.1.1 |
### نتیجهٔ verify و package
- `./mvnw -o -B -ntp clean verify` موفق شد. یک HTTP health test اجرا شد و تعداد failure، error و skip صفر بود. JAR اجرایی ساخته شد و `JAVA_HOME` صریحاً JDK 21 بود.
- `./mvnw -B -ntp -Psqlserver,redis,kafka,security -DskipTests package` در staging موفق شد. dependency‌ها resolve، compile و package شدند، اما تست‌ها عمداً skip شدند و سرویس خارجی بررسی نشد. هشدار 401 برای metadata برخی upstream repository‌ها جلوی موفقیت نهایی را نگرفت.
- بررسی لینک Markdown، code fence، whitespace و XML موفق بود. لینک‌ها و fence‌ها در مسیر نهایی هم دوباره بررسی شدند.
- این اجراها پیش از اولین commit عمومی بودند. وضعیت GitHub CI در Actions مخزن گزارش می‌شود و شاهد محلی جای آن را نمی‌گیرد.

### اصلاح‌ها و مرز شواهد

قبل از اجرای موفق، parent ناموجود و انتظار بیش‌ازحد دقیق تست از JSON اصلاح شدند. عدد کارایی ثبت نشده است. این اجراها موارد زیر را تأیید نمی‌کنند:

- منطق مالی، migration و اتصال دیتابیس؛
- runtime مربوط به Redis یا Kafka و سیاست JWT؛
- OTP، CDC، FX یا throughput.

معیار بعد، تکمیل تصمیم‌های P0 و سپس schema و SQL Server integration test واقعی در P1 است. build output و log‌ها generated و ignored هستند. فرمان‌ها برای بازتولید ثبت شده‌اند.

## بررسی حذف profile در ۲۰۲۶-۱۰-۲۲

| مشخصه | مقدار |
| --- | --- |
| JDK | JetBrains Runtime 21.0.11 |
| میزبان | macOS ARM64 |
- `./mvnw -B -ntp clean verify` موفق شد: یک HTTP smoke test و صفر failure، error و skip. profile مخصوص تست `smoke` فعال بود؛ اتصال SQL Server یا رفتار مالی بررسی نشد.
- incremental build اول موفق بود، اما YAML حذف‌شده در `target` مانده بود. clean build آن را حذف کرد. بررسی JAR حضور dependency‌های پیش‌فرض JPA و Flyway و Microsoft JDBC و نبود `application-sqlserver.yaml` و فایل تست `application-smoke.yaml` را تأیید کرد.
- startup عادی JAR پس از حذف override‌های DB و Spring، به‌دلیل `DB_URL` تنظیم‌نشده طبق انتظار fail شد. دیتابیس آماده نشد و اتصال موفقی نداشتیم.
- اجرای اول Maven در sandbox به cache دسترسی نداشت. نتیجه‌های بالا بعد از مجازشدن دسترسی cache و شبکه ثبت شدند.
- تصمیم تنظیمات: [ADR-006](../adr/ADR-006-default-sqlserver.fa.md).

## اجرای bootstrap توسط یادگیرنده در ۲۰۲۶-۱۰-۲۲

یادگیرنده خروجی ترمینال فرمان‌های زیر را ارائه کرد؛ پایان اجرا ساعت ۱۶:۲۲:۳۳ به وقت تهران بود:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -B -ntp clean verify
```

### نتیجهٔ ارائه‌شده توسط یادگیرنده

| معیار | نتیجه |
| --- | --- |
| enforcer | Java 21.0.11 پذیرفته شد |
| build | `BUILD SUCCESS` در ۶٫۸۸۹ ثانیه |
| تست | یک HTTP smoke test؛ صفر failure، error و skip |
| profile | `smoke` فعال |
| خروجی | JAR اجرایی ساخته شد |

Flyway metadata از بعضی repository‌ها هشدار 401 داشت و Mockito هشدار dynamic agent داد. هیچ‌کدام مانع تکمیل نشدند.

این خروجی فقط اجرای bootstrap توسط یادگیرنده را نشان می‌دهد. SQL Server، migration و رفتار مالی بررسی نشده‌اند. توضیح CBS00، استدلال حالت تغییریافته و شواهد باقی‌ماندهٔ Git/CI جدا ارزیابی می‌شوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

کاربر در ۲۰۲۶-۱۰-۲۲ گزینهٔ E را در [ADR-007](../adr/ADR-007-double-entry-ledger.fa.md) پذیرفت. دامنهٔ V1 اکنون چهار جدول دفترکل و projection هم‌زمان است. این تغییر طراحی، معنای نتیجه‌های build قبلی را عوض نمی‌کند.

build قبلی فقط شاهد bootstrap است؛ endpoint مالی، migration، صحت حسابداری، سازگاری SQL Server یا تسلط یادگیرنده را ثابت نمی‌کند.

### بررسی هم‌راستایی گزینهٔ E در ۲۰۲۶-۱۰-۲۲

- `./mvnw -B -ntp clean verify` با Java 21 در ۷٫۲۵۱ ثانیه موفق شد.
- یک HTTP test اجرا شد و failure، error و skip صفر بودند.
- تلاش اول در sandbox به Maven cache lock دسترسی نداشت؛ اجرای مجاز با cache و loopback موفق شد.
- تغییر جاوا فقط مستندسازی قرارداد بود و تست حسابداری SQL Server اجرا نشد.
- validator مسیر یادگیری vault بدون خطا تمام شد.
- این بررسی هیچ کار یادگیرنده‌ای را نپذیرفت.
