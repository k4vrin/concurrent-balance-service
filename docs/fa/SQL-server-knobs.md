# آزمایش قفل‌ها و مسیر دسترسی در SQL Server

[English](../en/SQL-server-knobs.md) · [تنظیمات تست](SQL-server-tests.md) · [فهرست مستندات](../README.md)

در انتقال وجه، حساب قفل‌شده همچنان یکی از طرف‌های انتقال است. رد کردن آن، معنای فرمان را تغییر می‌دهد. صف کار نیاز دیگری دارد: وقتی یک worker آیتم اول را گرفته، worker بعدی می‌تواند سراغ آیتم آزاد بعدی برود. SQL Server برای هر دو حالت ابزار دارد، اما درستی انتخاب به هدف query بستگی دارد.

کلاس [SqlServerKnobsIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerKnobsIT.java) این تنظیمات را روی داده‌های ساختگی بررسی می‌کند. تست‌ها از هویت محدود فعلی و کنترل اتصال به `cbs_test` استفاده می‌کنند. جدول‌های موقت سراسری با نام تولیدشده در `tempdb` ساخته و پس از هر تست صریحاً حذف می‌شوند. این آزمایش migration تجاری یا مجوز تازه‌ای لازم ندارد.

## هفت مورد چه چیزی را بررسی می‌کنند؟

| تعداد | آزمایش | انتظار تست |
| --- | --- | --- |
| ۳ | تغییر `ALLOW_ROW_LOCKS` و `ALLOW_PAGE_LOCKS` با `ALTER INDEX ALL` | تنظیم‌های row فعال و page غیرفعال، حالت عکس و غیرفعال بودن هر دو در `tempdb.sys.indexes` ثبت می‌شوند؛ جدول همچنان خواندنی است. |
| ۲ | اجرای `FORCESEEK` و `FORCESCAN` همراه `INDEX(1)` | هر دو دستور اجرا می‌شوند و مقدار انتخاب‌شدهٔ یکسانی برمی‌گردانند. index شمارهٔ ۱، کلید اصلی clustered همین fixture است. |
| ۱ | نگه‌داشتن `XLOCK, HOLDLOCK, ROWLOCK` روی کلید ۱ | خواندن عادی مبتنی بر قفل، خطای 1222 می‌گیرد. `READPAST` هنگام نگه‌داشتن قفل فقط کلید ۲ و پس از rollback هر دو کلید را برمی‌گرداند. |
| ۱ | نگه‌داشتن `XLOCK, HOLDLOCK, PAGLOCK` | خواننده حتی با `READPAST` تا آزاد شدن قفل، خطای 1222 می‌گیرد. |

session اول قبل از شروع خواننده قفل را می‌گیرد و پس از بستن result set هم آن را نگه می‌دارد. ایجاد contention به sleep یا زمان‌بندی threadها وابسته نیست. خواننده `LOCK_TIMEOUT 250` دارد و helper مربوط به JDBC هم انتظار شبکه را محدود می‌کند. هر دو session از `READ_COMMITTED` استفاده می‌کنند؛ `READCOMMITTEDLOCK` حتی در صورت فعال بودن RCSI، خواندن مبتنی بر قفل را انتخاب می‌کند. این fixture دو ردیف دارد و lock escalation زیر بار را بررسی نمی‌کند.

## برداشت دقیق از ادعا

گزینه‌های `ALLOW_ROW_LOCKS` و `ALLOW_PAGE_LOCKS` مشخص می‌کنند کدام نوع قفل برای index مجاز است. فعال بودن یک نوع، استفادهٔ همیشگی از آن را تضمین نمی‌کند. با غیرفعال شدن هر دو هم قفل در سطح جدول ممکن است. تست‌های metadata تنظیم را بررسی می‌کنند و تست‌های دو session، رفتار قابل‌مشاهدهٔ blocking و رد کردن ردیف را با hint صریح می‌سنجند. این تست‌ها تمام قفل‌های داخلی را فهرست نمی‌کنند و نبود escalation را ثابت نمی‌کنند. مرجع Microsoft: [گزینه‌های index](https://learn.microsoft.com/en-us/sql/t-sql/statements/alter-table-index-option-transact-sql?view=sql-server-ver16) و [تنظیم قفل index](https://learn.microsoft.com/en-us/sql/relational-databases/system-stored-procedures/sp-indexoption-transact-sql?view=sql-server-ver17).

`FORCESEEK` و `FORCESCAN` انتخاب‌های optimizer را محدود می‌کنند. این hintها از یک ترجیح ساده قوی‌ترند و ممکن است plan مفید را حذف کنند یا ساخت plan را ناممکن کنند. دو تست فقط پذیرش hint و خروجی درست را بررسی می‌کنند. XML مربوط به execution plan، تعداد logical read و مقایسهٔ سرعت در این آزمایش ثبت نمی‌شود. بررسی کارایی به تعداد ردیف واقع‌بینانه، actual plan و `STATISTICS IO/TIME` نیاز دارد؛ مجوز احتمالی SHOWPLAN هم باید جداگانه بررسی شود. مرجع: [table hintها در Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table?view=sql-server-ver17).

`READPAST` از قفل ردیف عبور می‌کند، اما از قفل page عبور نمی‌کند. مقدار commitنشده را نمی‌خواند و کامل بودن نتیجه یا نبود هر نوع blocking را تضمین نمی‌کند. ردیف حذف‌شده پس از آزاد شدن قفل دوباره دیده می‌شود. مستندات SQL Server این قابلیت را بیشتر برای صف کار توضیح می‌دهند و برای isolation level و RCSI محدودیت دارند. به همین دلیل تست‌ها صریحاً `READCOMMITTEDLOCK` دارند. مرجع: همان [مستند table hintها](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table?view=sql-server-ver17).

این‌که تقریباً هر نیاز خاصی با تنظیمات حل می‌شود، یک نظر است و این تست‌ها آن را ثابت نمی‌کنند. هر تنظیم تازه، ترکیب‌های بیشتری برای بازبینی و تست ایجاد می‌کند.

## اجرای آزمایش

محیط آماده‌شدهٔ SQL Server را طبق [Docker.md](Docker.md) اجرا کنید. متغیرهای اختصاصی `SQLSERVER_TEST_URL`، `SQLSERVER_TEST_USERNAME` و `SQLSERVER_TEST_PASSWORD` را طبق [راهنمای تست](SQL-server-tests.md) export کنید. با Java 21:

```bash
./mvnw -Psqlserver-it -Dit.test=SqlServerKnobsIT verify
```

ابتدا تست‌های portable اجرا می‌شوند. نبود زیرساخت، setup کلاس را fail می‌کند و باعث skip نمی‌شود. گزارش تازهٔ `target/failsafe-reports/TEST-dev.kavrin.balance.infrastructure.sqlserver.SqlServerKnobsIT.xml` باید هفت تست و صفر failure، error و skip داشته باشد. برای regression کامل، `./mvnw -Psqlserver-it verify` را اجرا کنید. دستور عادی `./mvnw verify` این کلاس را compile می‌کند، ولی تست‌های واقعی SQL Server را اجرا نمی‌کند.

## مرز کاربرد در پروژه

این آزمایش قفل حساب، بازیابی transaction ID، نوشتن دفترکل، مانده‌ها یا migrationها را تغییر نمی‌دهد. `READPAST` در lookup فعلی حساب، بررسی موجودی، lookup نتیجهٔ قبلی یا reconciliation جایگاهی ندارد؛ رد کردن حساب، نتیجه یا posting لازم، پاسخ را تغییر می‌دهد. worker مربوط به outbox آینده می‌تواند الگوی claim از صف را با قرارداد اتمیک و retry مستقل بررسی کند. این تغییر outbox پیاده‌سازی نمی‌کند.

از این آزمایش‌های ساختگی، نتیجهٔ benchmark یا افزایش شمار تست‌های پذیرش هسته برداشت نکنید. اجرای محلی SQL Server با شبیه‌سازی ARM64 همچنان آزمایش پشتیبانی‌نشده‌ای است که راهنمای محیط توضیح می‌دهد.

## نتیجهٔ ثبت‌شده در ۲۰۲۶-۱۰-۳۰

اجرای متمرکز با Java 21.0.11، SQL Server 16.00.4225 و Microsoft JDBC 13.4.0.0، شامل **۴۲۱ تست portable و ۷ تست SQL Server** بود و در 9.464 ثانیه با صفر failure، error و skip گذشت. سپس دستور کامل `./mvnw -Psqlserver-it verify`، **۴۲۱ تست portable و ۲۱۹ تست SQL Server** را در 01:09 دقیقه با صفر failure، error و skip گذراند. مجموع‌ها از XML تازهٔ Surefire و Failsafe بررسی شدند؛ هفت مورد این آزمایش در اجرای کامل 0.924 ثانیه زمان بردند. این تست‌های موتور، دامنهٔ پذیرش مالی را گسترش نمی‌دهند.

تلاش‌های اولیه قبل از اجرای آزمایش شکست خوردند: تنظیمات اختصاصی تست هنوز export نشده بود، Docker به تعمیر helper دارای مجوز نیاز داشت و سپس کانتینر موجود با سقف ۴ گیگابایت هنگام startup با خطای 701 مربوط به internal memory pool خارج شد. اجراهای موفق با سقف موقت ۶ گیگابایت برای کانتینر انجام شدند؛ تنظیم حافظهٔ خود SQL Server همان ۲٬۰۴۸ مگابایت ماند. پس از بررسی، کانتینر متوقف و سقف ۴ گیگابایت بازگردانده شد. تنظیمات Compose مخزن و volumeهای پایگاه‌داده تغییر نکردند. این مشاهده یک راه‌حل محلی برای startup است و توصیهٔ ظرفیت بر پایهٔ اندازه‌گیری محسوب نمی‌شود.
