> به‌روزرسانی پیاده‌سازی CBS09 در ۲۰۲۶-۱۰-۲۳: V1 در دیتابیس محلی توسعه/تست اعمال و validate شده است. [schema و راهنما](Schema.md) قید، grant و شواهد ۲۴۰ تست مستقل از زیرساخت و ۹۴ تست SQL Server را ثبت می‌کند. گفته‌های تاریخی پیش از migration در ادامه با این شواهد جایگزین می‌شوند. اجرای مالی هنوز پیاده نشده است.

# harness تست واقعی SQL Server در CBS08

[English](../en/SQL-server-tests.md) · [setup محیط](Docker.md) · [شواهد CBS07](SQL-server-evidence.md) · [نقشهٔ راه](Roadmap.md)

profile Maven موسوم به sqlserver-it تست زیرساخت را روی دیتابیس آمادهٔ cbs_test اجرا می‌کند. verify عادی Portable می‌ماند. این harness خودکار JDBC است و شاهد use case مالی، JPA/Flyway یا مسیر HTTP تا ledger نیست.

## profile چه چیزی را تغییر می‌دهد؟

[pom.xml](../../pom.xml) فقط با `-Psqlserver-it`، Maven Failsafe 3.5.6 را برای `**/*IT.java` فعال می‌کند و نبود تست را fail می‌کند. parent مربوط به Spring Boot از قبل integration-test و verify را bind می‌کند؛ execution تکراری نداریم. lifecycle را تا verify اجرا کنید، نه فقط integration-test، تا نتیجهٔ Failsafe بررسی شود. [مرجع lifecycle](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html).

این profile اجرای تست را کنترل می‌کند و dependency یا startup برنامه را عوض نمی‌کند. SQL Server همچنان dependency پیش‌فرض برنامه است. این harness مستقیم JDBC، profile زمان اجرای Spring متناظر نمی‌خواهد. تست Portable از نوع Surefire ابتدا اجرا می‌شود؛ تست HTTP smoke که persistence را خاموش می‌کند در context خودش مجاز است. harness یکپارچه جداگانه smoke را در تنظیم فعال/include/default محیط یا system رد می‌کند.

## datasource صریح تست و محافظ‌ها

[SqlServerTestDatabase](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTestDatabase.java) هر سه متغیر را اجباری می‌کند:

| متغیر | معنی |
| --- | --- |
| SQLSERVER_TEST_URL | URL صریح JDBC SQL Server با databaseName=cbs_test |
| SQLSERVER_TEST_USERNAME | login غیرadmin اختصاصی؛ cbs_app در setup محلی |
| SQLSERVER_TEST_PASSWORD | راز مطابق، بیرون Git |

datasource پیش‌فرض یا fallback به DB_URL/DB_USERNAME/DB_PASSWORD نداریم. مقدار غایب/blank در setup کلاس fail می‌شود. credential داخل propertyهای URL مثل user/userName/password/accessToken رد می‌شود و باید در محیط جدا باشد. اتصال واقعی پیش از هر write باید محصول Microsoft SQL Server و catalog دقیق cbs_test را گزارش کند. بررسی catalog واقعی، property هدایت‌شده/overrideشدهٔ URL را هم پوشش می‌دهد. اتصال cbs_dev بسته و رد می‌شود.

انتظار login و socket با propertyهای driver به پنج ثانیه محدود است. helper، credential یا URL را چاپ نمی‌کند. driver واقعی Microsoft JDBC پروژه استفاده می‌شود و H2، Oracle، mock یا فرض skip به‌علت نبود DB نداریم. [تست Portable محافظ‌ها](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTestDatabaseTests.java) بدون اتصال، این قواعد را بررسی می‌کند.

## اجرای Bash

ابتدا محیط CBS07 را آماده و کانتینر SQL Server را healthy نگه دارید. با JAVA_HOME روی Java 21:

```bash
export SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true'
export SQLSERVER_TEST_USERNAME=cbs_app
read -r -s -p 'Test login password: ' SQLSERVER_TEST_PASSWORD
export SQLSERVER_TEST_PASSWORD
./mvnw -Psqlserver-it verify
unset SQLSERVER_TEST_PASSWORD
```

رمز application محلی را از فایل محیط ignored وارد کنید؛ رمز جدید یا رمز administrator نیست. read در Bash ورودی را پنهان می‌کند و راز وارد command history نمی‌شود. فایل .env.sqlserver مربوط به Compose خودکار وارد Maven نمی‌شود. استثنای محلی trustServerCertificate=true در راهنمای setup توضیح داده شده و سیاست TLS استقرار خارجی نیست.

## اجرای PowerShell

JAVA_HOME را روی JDK 21 بگذارید و همان رمز app محلی را وارد کنید:

```powershell
$env:SQLSERVER_TEST_URL = 'jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true'
$env:SQLSERVER_TEST_USERNAME = 'cbs_app'
$testSecret = Read-Host 'Test login password' -AsSecureString
$env:SQLSERVER_TEST_PASSWORD = [System.Net.NetworkCredential]::new('', $testSecret).Password
try {
    .\mvnw.cmd -Psqlserver-it verify
} finally {
    Remove-Item Env:SQLSERVER_TEST_PASSWORD -ErrorAction SilentlyContinue
}
```

ورودی امن نمایش/history را محافظت می‌کند؛ driver همچنان راز محیط process را می‌گیرد. این secret manager تولید نیست. رمز را با -D ندهید و راز resolveشده را وارد شواهد build نکنید.

## پنج مورد integration

[SqlServerEnvironmentIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerEnvironmentIT.java) در نبود تنظیم/زیرساخت، BeforeAll را fail می‌کند. Spring یا migration اجرا نمی‌کند.

| تست | رفتار واقعی دیتابیس |
| --- | --- |
| selectUsesActualSqlServerAndNonAdministratorLogin | SELECT، catalog، هویت driver و اتصال غیرsysadmin |
| rollbackRemovesUncommittedLocalFixtureRows | جدول موقت محلی پیش از تلاش وجود دارد؛ تعداد سطر درج‌شده پس از rollback صریح از یک به صفر می‌رسد |
| committedSetupIsVisibleToAnotherConnection | fixture مصنوعی commit‌شده از اتصال JDBC دوم دیده می‌شود |
| rollingBackAnAttemptPreservesSeparateCommittedSetup | update نویسندهٔ جدا rollback می‌شود؛ reader تازه مقدار commit‌شدهٔ اصلی 7 را می‌بیند |
| fixtureCleanupRemovesOnlyItsOwnTemporaryTable | close صریح، آبجکت موقت تولیدشده را حذف می‌کند؛ اتصال دیگر با OBJECT_ID بررسی می‌کند |

[CommittedSqlServerFixture](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/CommittedSqlServerFixture.java) جدول موقت global با نام `##cbs08_<generated UUID>` می‌سازد، سطر مصنوعی را commit و اتصال مالک را تا cleanup باز نگه می‌دارد. جدول global بین اتصال‌ها دیده می‌شود؛ جدول محلی # محدود به session خودش است. عمر پیش‌فرض global temp هم به session سازنده و referenceهای فعال وابسته است. در close عادی، آبجکت مشخص DROP می‌شود و cleanup سراسری دیتابیس نداریم. [رفتار temporary table در Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-table-transact-sql).

هر fixture شناسهٔ تولیدشدهٔ مستقل دارد و identifier از ورودی کاربر نمی‌آید. try-with-resources هنگام شکست assertion هم cleanup می‌کند. global temp فضای نام مشترک سرور دارد و جداسازی امنیتی نیست؛ فقط دادهٔ مصنوعی دارد. در cbs_dev/cbs_test جدول ساخته نمی‌شود، business row seed نمی‌شود و نقش db_owner/DDL به cbs_app اضافه نمی‌شود.

fixture مربوط به rollback محلی و setup commit‌شدهٔ چنداتصالی عمداً مرز تراکنش جدا دارند. قراردادن همهٔ تست در یک تراکنش rollback-only اسپرینگ، visibility setup commit‌شده به اتصال دیگر را اثبات نمی‌کند. fixture افتتاحیه/حسابداری بعد از mappingهای CBS09–10 باید از مسیر واقعی posting مرورشده ساخته شود؛ سطر موقت جایگزین آن نیست.

## اثبات fail به‌جای skip پنهان

با credential تست exportشده، endpoint صریح ناموجود باید Maven را fail کند:

```bash
SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:1;databaseName=cbs_test;encrypt=true;trustServerCertificate=true' \
  ./mvnw -Psqlserver-it verify
```

در PowerShell موقتاً SQLSERVER_TEST_URL را همین URL ناموجود بگذارید، verify را اجرا و سپس URL مربوط به cbs_test را برگردانید. این بررسی کانتینر مشترک را متوقف یا دیتابیس دیگری را تغییر نمی‌دهد.

نبود URL تست باید در تنظیم fail شود. smoke صریح حتی با DB سالم باید fail شود. URL مربوط به cbs_dev باید پیش از fixture write در guard مربوط به catalog رد شود. شکست عمدی negative check شاهد محافظ است و تست integration موفق نیست. سپس تنظیم معتبر را برگردانید و گزارش مثبت Failsafe بخواهید.

## گزارش، شواهد و gateهای باقی‌مانده

تست Portable در target/surefire-reports و integration در target/failsafe-reports گزارش می‌نویسد. تعداد، failure، error و skip مربوط به XML اجرای جدید را بررسی کنید؛ اجرای Portable-only ممکن است گزارش قدیمی Failsafe را نگه دارد. failIfNoTests مجموعهٔ کشف‌شدهٔ خالی را موفق نشان نمی‌دهد؛ skip flag عمدی Maven همچنان به معنی بررسی‌نشدن آن suite است.

این پیاده‌سازی فقط temporary table زیرساخت دارد. پاس‌بودن، mapping مالی BIGINT، entityهای JPA، مجوز Flyway، قفل حساب، write اتمیک ledger، replay ماندگار، rollback مالی یا مجوز را اثبات نمی‌کند. محیط SQL Server فعلی روی ARM64 همچنان آزمایش emulation پشتیبانی‌نشده است.

پیش از CBS09، مرور CBS02–04 مربوط به قید/هویت/تراکنش، سقف عددی تجاری، مقایسهٔ دقیق transaction ID و مجوز migration/runtime باید تمام شود. collation پیش‌فرض CI_AS و runtime از نوع CONNECT-only، آن تصمیم‌های نهایی نیستند. CBS08 migration تجاری اضافه نمی‌کند و پذیرش رسمی یادگیرنده از تست دستیار جدا می‌ماند.

## شواهد ثبت‌شدهٔ CBS08 در ۲۰۲۶-۱۰-۲۳

اجرای عادی `./mvnw verify` با Java 21.0.11 بدون datasource تست در ۱۲٫۰۹۲ ثانیه ۲۴۰ تست Portable را بدون failure، error یا skip گذراند. اجرای نهایی `./mvnw -Psqlserver-it verify` همان ۲۴۰ مورد و پنج تست SQL Server واقعی را در ۹٫۱۵۰ ثانیه، همگی بدون failure، error یا skip گذراند. پنج مورد integration برابر ۰٫۶۷۹ ثانیه زمان بردند. تعدادها در هر دو مجموعهٔ تازهٔ XML بررسی شدند.

چهار اجرای جدا با دلیل موردانتظار fail شدند: پورت loopback ناموجود 1، نبود SQLSERVER_TEST_URL، مقدار صریح SPRING_PROFILES_ACTIVE=smoke و اتصال URL به cbs_dev. negative checkها ۱۷ مورد Portable محافظ و setup کلاس integration را انتخاب کردند و تست دیتابیس موفق اضافه محسوب نمی‌شوند. اجرای کامل مثبت نهایی، گزارش‌ها را پس از شکست عمدی برگرداند. نبود مقدار راز محلی در log نهایی و گزارش‌ها بررسی شد. شواهد فقط مربوط به آزمایش فعلی پشتیبانی‌نشدهٔ ARM64 با SQL Server 2022 CU22 هستند.

بررسی پس از suite با login اختصاصی، صفر fixture موقت باقی‌مانده از نوع ##cbs08_ و صفر جدول کاربر در cbs_test نشان داد. مجوز administrator یا schema تجاری اضافه نشد.

## CBS02–04 review update, 2026-10-23

سقف مشترک و رد ماندگار بالاتر از آن اکنون [سیاست پذیرفته‌شدهٔ کسب‌وکار](Amount-limits.md) هستند. چهار مورد تازهٔ JDBC واقعی، نتیجهٔ آخرین profile را به ۲۴۰ تست مستقل از زیرساخت و ۹ تست SQL Server بدون failure، error یا skip رساندند. [مشاهده‌ها، محدودیت‌ها و معیارهای باقی‌ماندهٔ migration](Persistence-review.md) را ببینید. این آزمایش‌ها schema کسب‌وکار اضافه نمی‌کنند و مرور را کامل نمی‌کنند. آمار قبلی CBS08 شواهد تاریخی است.

## گام مرور شناسهٔ تراکنش، ۲۰۲۶-۱۰-۲۳

گزینهٔ فنی تست‌شده nvarchar(256) با collation صریح BIN2، NOT NULL، قید طول و کاراکتر برای ۱ تا ۱۲۸ کاراکتر و یکتایی سراسری با نام مشخص است. binding از نوع Unicode در JDBC، ورودی نامعتبر را برای ردشدن حفظ می‌کند. سی‌ویک مورد واقعی تازه شامل بررسی همهٔ واحدهای کد منفرد BMP و درج رقیب هستند. اجرای کامل profile با Java 21 در ۱۲٫۷۵۶ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۴۰ تست SQL Server را بدون failure، error یا skip گذراند؛ کلاس تازه ۲٫۰۹۱ ثانیه طول کشید. [طراحی، SQL، مثال‌ها و محدودیت‌ها](Transaction-id-storage.md) را ببینید. آمار قبلی تاریخی است. migration کسب‌وکار، repository زمان اجرا یا replay ماندگار اضافه نشده است. گام بعد، مرور adapter قفل حساب UUID و قرارداد کامل تراکنش چهار جدول و مجوزهاست؛ پذیرش رسمی یادگیرنده باز می‌ماند.

## تکمیل مرور مهندسی CBS02–04 برای CBS09، ۲۰۲۶-۱۰-۲۳

[ADR-010](../adr/ADR-010-persistence-contract.fa.md) و [قرارداد پیاده‌سازی CBS09](CBS09-contract.md) تصمیم‌های باقی‌مانده را یکجا مشخص می‌کنند. چهار مورد تازهٔ واقعی UUID، round trip نگاشت و BIGINT، تفاوت ترتیب برنامه و SQL، blocking و پیشرفت کلید دیگر، محافظت ساخت کلید غایب و workerهای جهت مخالف را می‌سنجند. اجرای کامل profile در ۱۶٫۹۹۷ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۴۴ تست SQL Server را بدون failure، error یا skip گذراند. SQL بومی صریح EntityManager، کلیدها/state ردیف/FK ارز در چهار جدول، نگه‌داری سیاست/پاسخ، تنظیم retry و اختیار جدا برای migration/runtime/initializer انتخاب شدند. گام بعد migration در CBS09 و تست واقعی قید/مجوز/Flyway است؛ مرورهای کوچک طراحی ادامه پیدا نمی‌کنند. تست خرابی مالی در CBS10–20 می‌ماند. DDL کسب‌وکار، grant یا پذیرش یادگیرنده اضافه نشده است.
