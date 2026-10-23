# شواهد محیط SQL Server در CBS07

[English](../en/SQL-server-evidence.md) · [setup و چرخهٔ اجرا](Docker.md) · [نقشهٔ راه](Roadmap.md)

ثبت در ۲۰۲۶-۱۰-۲۳. کاربر license مربوط به Developer را صریح پذیرفت و دانلود/اجرای آزمایش محلی emulation روی ARM64 را مجاز کرد. این شواهد، محیط و اتصال JDBC واقعی را بررسی می‌کنند؛ رفتار مالی یا پشتیبانی رسمی مایکروسافت روی ARM64 نیستند.

| مورد | مقدار مشاهده‌شده |
| --- | --- |
| Docker Desktop / engine | 4.42.0 / 28.2.2 |
| Docker Compose | 2.36.2-desktop.1 |
| معماری daemon / حافظهٔ VM | linux/arm64 با گزارش aarch64؛ 8,218,316,800 بایت |
| image | mcr.microsoft.com/mssql/server:2022-CU22-ubuntu-22.04 |
| digest manifest و image اجراشده | sha256:db9a8fe3098b7e8bbde41106bdc7caee942e97124e5fdb71b872ca208de3092d |
| platform درخواستی | linux/amd64 با emulation |
| کانتینر | cbs-local-sqlserver-1، healthy |
| endpoint منتشرشده | 127.0.0.1:14333 به TCP 1433 کانتینر |
| volume ماندگار | cbs-local_sqlserver-data در /var/opt/mssql |
| سقف تنظیم‌شدهٔ حافظهٔ کانتینر / سرور | 4,294,967,296 بایت / ۲۰۴۸ MB |
| نسخهٔ SQL Server | 16.0.4225.2 |
| edition | Developer Edition (64-bit) |
| collation سرور و دیتابیس‌ها | SQL_Latin1_General_CP1_CI_AS |
| دیتابیس توسعه / تست | cbs_dev / cbs_test |
| compatibility هر دو | 160 |
| READ_COMMITTED_SNAPSHOT هر دو | 0 |
| snapshot isolation هر دو | OFF |
| encryption اتصال sqlcmd | TRUE |
| JDBC driver | Microsoft JDBC Driver 13.4 for SQL Server، نسخهٔ 13.4.0.0 |
| نسخهٔ سرور در JDBC | 16.00.4225 |
| login اختصاصی | cbs_app با CONNECT در هر دیتابیس |
| sysadmin / CREATE TABLE برنامه | 0 / 0 در هر دو؛ guard مربوط به db_owner هم موفق شد |
| جدول کاربر | صفر در هر دو |

## چه چیزهایی اجرا شد؟

1. manifest از registry مایکروسافت resolve و digest پیش از pin بررسی شد. همان image پس از مجوز دانلود و اجرا شد.
2. Compose با placeholder و بدون چاپ راز validate شد. syntax shell و compile مستقل probe با Java 21 بررسی شدند.
3. چهار شکل نامعتبر رمز برنامه پیش از sqlcmd رد شدند. credential واقعی تصادفی در فایل ignored از نوع mode-0600 به نام .env.sqlserver است و در شواهد نیست.
4. bootstrap زیرساخت، دیتابیس خالی، login و user ساخت؛ metadata، encryption و مجوز برنامه بررسی شدند.
5. driver واقعی Microsoft JDBC پروژه از host به هر دو دیتابیس وصل و با login غیرadmin، SELECT اجرا کرد.
6. bootstrap بدون حذف داده یا تغییر رمز دوباره اجرا شد.
7. کانتینر restart شد، health موفق شد و JDBC به هر دو دیتابیس **پیش از** اجرای دوبارهٔ bootstrap وصل شد. این بقای دیتابیس/login را در همان restart با volume حفظ‌شده نشان می‌دهد؛ شاهد backup/recovery، خرابی دیسک یا بازیابی روی میزبان پاک نیست.
8. bootstrap پس از restart هم موفق شد و صفر جدول کاربر در هر دو گزارش کرد.
9. probe JDBC با پورت loopback ناموجود 1، exit غیرصفر داد. این شاهد fail شدن probe است و هنوز gate تست Maven بدون DB در CBS08 نیست.

اجرای کامل `./mvnw verify` همراه ساخت classpath با Java 21.0.11 در ۹٫۸۲۷ ثانیه ۲۲۳ تست Portable را بدون failure، error یا skip گذراند. probeهای بالا بررسی دستی integration اضافه‌اند و در شمار تست Failsafe نیستند.

## محدودیت و تحویل مرحلهٔ بعد

مایکروسافت emulation کانتینر SQL Server روی ARM64 را پشتیبانی نمی‌کند. startup سالم و SELECT شاهد آزمایش محلی است؛ برای محیط رسمی روی میزبان Linux x86-64 تکرار لازم است. ادعای performance، load یا آمادگی production نداریم.

certificate خودساخته برای sqlcmd/JDBC محلی صریح trusted شده است. encryption TRUE هویت معتبر سرور را اثبات نمی‌کند. پیش‌فرض driver/protocol و استقرار خارجی با certificate معتبر مرور جدا می‌خواهند.

collation پیش‌فرض CI_AS، semantics حساس به بزرگی حروف TransactionId را ثابت نمی‌کند. CBS02–04 باید مقایسهٔ ستون، قیدها، SQL قفل/تراکنش، سقف تجاری و مجوز migration/runtime را پیش از CBS09 ببندد. login از نوع CONNECT-only جدول Flyway/تجاری نمی‌سازد؛ startup مالی موفق Spring ادعا نشده است.

جدول accounts، operations، journals یا postings نداریم و موجودی seed نشده است. handler مالی، fingerprint/outcome ذخیره‌شده، replay ماندگار و تراکنش ledger نداریم. CBS08 هنوز Failsafe sqlserver-it، datasource مستقل صریح، تعداد تست مثبت، fail بدون DB، rollback و سپس fixture commit‌شدهٔ چنداتصالی می‌خواهد.

کانتینر مجاز روی loopback روشن می‌ماند. فرمان stop/down/reset در [Docker.md](Docker.md) است. volume حذف نشده و credential وارد commit نمی‌شود. پذیرش رسمی، checkpoint مستقل قبلی یادگیرنده و وضعیت مرور CBS02–04 تغییر نکرده‌اند.
