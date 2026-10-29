> راه‌اندازی فعلی: برای API محلی و درخواست‌های اجرایی، [walkthrough کامل HTTP](HTTP-samples.md) را دنبال کنید. [هستهٔ CBS20](Core-acceptance.md) کامل است؛ توضیح‌های تاریخ‌دار زیر، checkpoint اولیهٔ خود را شرح می‌دهند.

> به‌روزرسانی پیاده‌سازی CBS09 در ۲۰۲۶-۱۰-۲۳: V1 در دیتابیس محلی توسعه/تست اعمال و validate شده است. [schema و راهنما](Schema.md) قید، grant و شواهد ۲۴۰ تست مستقل از زیرساخت و ۹۴ تست SQL Server را ثبت می‌کند. گفته‌های تاریخی پیش از migration در ادامه با این شواهد جایگزین می‌شوند. اجرای مالی هنوز پیاده نشده است.

# SQL Server محلی با Docker Compose

[English](../en/Docker.md) · [فهرست](../../README.fa.md) · [مرور دیتابیس](Database-review.md)

آزمایش محلی CBS07 در ۲۰۲۶-۱۰-۲۳: پس از پذیرش صریح license و مجوز کاربر، کانتینر pinned سالم است، JDBC واقعی به هر دو دیتابیس خالی وصل شد و دیتابیس‌ها از restart کانتینر عبور کردند. [شواهد مشاهده‌شده](SQL-server-evidence.md). این emulation پشتیبانی‌نشدهٔ ARM64 است و شاهد محیط رسمی یا صحت مالی نیست. پذیرش رسمی باز است.

## تنظیم منتخب و مرز پشتیبانی

[compose.yaml](../../compose.yaml) نسخهٔ SQL Server 2022 CU22 روی Ubuntu 22.04 با edition نوع Developer را با tag و digest تغییرناپذیر مشخص می‌کند:

```text
mcr.microsoft.com/mssql/server:2022-CU22-ubuntu-22.04
sha256:db9a8fe3098b7e8bbde41106bdc7caee942e97124e5fdb71b872ca208de3092d
```

registry مایکروسافت این digest را در ۲۰۲۶-۱۰-۲۳ برگرداند. این baseline قابل‌بازتولید منتخب است و ادعای جدیدترین security update نیست. image دانلود/اجراشده و build/edition در [شواهد setup](SQL-server-evidence.md) ثبت شده‌اند. مسیر sqlcmd برابر `/opt/mssql-tools18/bin/sqlcmd` است که imageهای SQL Server 2022 از CU14 به بعد دارند. [مرجع مایکروسافت](https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker).

مایکروسافت کانتینر Linux SQL Server را روی میزبان Linux با Intel/AMD x86-64 پشتیبانی می‌کند. emulation/translation نوع ARM64 از جمله Rosetta/QEMU پشتیبانی نمی‌شود. linux/amd64 در Compose فقط آزمایش محلی این Mac را ممکن می‌کند و اثبات سازگاری نیست. اگر آزمایش شکست خورد یا شاهد روی محیط پشتیبانی‌شده لازم بود، میزبان Docker با x86-64 انتخاب کنید؛ H2/Oracle یا engine دیگر جایگزین شاهد نیست. [مرز معماری](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment).

سرویس به‌طور پیش‌فرض فقط `127.0.0.1:14333` را منتشر می‌کند. دادهٔ `/var/opt/mssql` در volume نام‌دار پروژه می‌ماند، readiness محدود دارد و خودکار restart نمی‌شود. محدودیت حافظهٔ SQL Server برابر ۲۰۴۸ MB و کانتینر ۴ GB است؛ انتخاب setup محلی است و توصیهٔ ظرفیت اندازه‌گیری‌شده نیست. VM مربوط به Docker باید حافظه و دیسک کافی برای اجرا و extract image داشته باشد.

فقط SQL Server اضافه شده است؛ Redis، Kafka و Connect/Debezium برای مرحلهٔ خود می‌مانند. برنامه فعلاً روی host اجرا می‌شود. startup عادی Spring دیتابیس تنظیم‌شده می‌خواهد، اما این setup شاهد startup برنامه یا migration نیست.

## رازهای بیرون Git و انتخاب license

[.env.example](../../.env.example) را به `.env.sqlserver` نادیده‌گرفته‌شده کپی و محلی تکمیل کنید:

- `ACCEPT_EULA=Y` فقط پس از پذیرش صریح license مربوط SQL Server Developer برای توسعه/تست.
- رمزهای متمایز `MSSQL_SA_PASSWORD` و `CBS_APP_PASSWORD`؛ مقدار واقعی در Git، تصویر و شواهد نباشد.
- رمز برنامه ۱۶ تا ۱۲۸ کاراکتر ASCII از حرف/رقم/underscore/hyphen با حرف بزرگ، کوچک و رقم باشد. این محدودیت bootstrap برای جلوگیری از SQL metacharacter در جایگزینی متغیر sqlcmd است، نه تعریف عمومی syntax رمز SQL Server.
- پورت پیش‌فرض SQLSERVER_PORT برابر 14333 است؛ اگر اشغال است پورت loopback دیگری بدهید.

```sh
cp .env.example .env.sqlserver
chmod 600 .env.sqlserver
# Edit the ignored file locally. Do not paste its values into chat or logs.
docker compose --env-file .env.sqlserver config --quiet
```

برای validation از config --quiet استفاده کنید؛ config عادی یا config --environment می‌تواند راز جایگزین‌شده را چاپ کند. کاربر دارای دسترسی Docker inspection، متغیر کانتینر را می‌بیند؛ این setup secret store تولید نیست. Spring فایل env مربوط به Compose را خودکار نمی‌خواند. [متغیرهای Compose](https://docs.docker.com/reference/compose-file/interpolation).

## شروع، bootstrap و بررسی

فرمان‌ها سرویس اجرا می‌کنند، در صورت نبود image آن را دانلود و volume ماندگار ایجاد می‌کنند. فقط پس از انتخاب license/منابع بالا اجرا شوند:

```sh
docker version
docker compose version
docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver
docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh
docker compose --env-file .env.sqlserver ps
docker compose --env-file .env.sqlserver logs --tail 100 sqlserver
```

[bootstrap.sh](../../scripts/sqlserver/bootstrap.sh) راز برنامه را پیش از فراخوانی [bootstrap.sql](../../scripts/sqlserver/bootstrap.sql) بررسی می‌کند. SQL فقط دیتابیس خالی `cbs_dev` و `cbs_test` و login/user اختصاصی `cbs_app` با CONNECT می‌سازد. نسخه، edition و collation سرور، compatibility و وضعیت snapshot دیتابیس و encryption اتصال را چاپ می‌کند. shell اتصال app و مجوز sysadmin/CREATE TABLE را برای هر دو دیتابیس بررسی می‌کند. schema مالی یا seed پول ندارد.

اجرای دوباره دیتابیس، login و user موجود را استفاده می‌کند؛ داده حذف و رمز عوض نمی‌کند. اگر فایل env با login حفظ‌شده سازگار نباشد، verification fail می‌شود. راز مطابق را برگردانید یا rotation صریح و مرورشده انجام دهید؛ برای تعمیر اتصال volume حذف نکنید. دو دیتابیس نام/داده را جدا می‌کنند اما login محلی مشترک دارند؛ هویت مستقل محیط‌ها انتخاب استقرار بعدی است.

login برنامه عمداً db_owner، نقش ارتقایافتهٔ سرور و نقش DDL ندارد. مجوز migration/runtime در CBS02–04/CBS09 پیش از migration مالی مرور می‌شود. این login به‌تنهایی Flyway history یا جدول تجاری نمی‌سازد؛ startup عادی برنامه را با این bootstrap موفق اعلام نکنید. sa فقط مدیریت اولیه است، نه runtime برنامه.

گزینهٔ sqlcmd -C به certificate محلی اعتماد می‌کند. JDBC از encrypt=true با استثنای صریح trustServerCertificate=true فقط برای توسعهٔ loopback استفاده می‌کند؛ transport رمز می‌شود اما هویت سرور با certificate معتبر بررسی نمی‌شود. استقرار خارجی certificate/hostname/trust درست می‌خواهد. [متغیرهای sqlcmd](https://learn.microsoft.com/en-us/sql/tools/sqlcmd/sqlcmd-use-scripting-variables).

## اتصال با JDBC واقعی پروژه

[SqlServerProbe.java](../../scripts/sqlserver/SqlServerProbe.java) probe اتصال با source-mode جاوای ۲۱ است و harness CBS08 نیست. از classpath واقعی پروژه استفاده می‌کند، credential را از محیط می‌گیرد، SELECT می‌زند و اتصال app با sysadmin را رد می‌کند. URL یا رمز چاپ نمی‌کند، Spring اجرا نمی‌کند، schema تغییر نمی‌دهد و تراکنش تست نمی‌کند.

با JAVA_HOME روی Java 21 و DB_URL، DB_USERNAME=cbs_app و DB_PASSWORD مطابق راز محلی exportشده:

```sh
./mvnw dependency:build-classpath -Dmdep.outputFile=/tmp/cbs-jdbc-classpath
"$JAVA_HOME/bin/java" --class-path "$(cat /tmp/cbs-jdbc-classpath)" scripts/sqlserver/SqlServerProbe.java
```

URL توسعه برابر `jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_dev;encrypt=true;trustServerCertificate=true` است؛ به‌دلیل semicolon در shell داخل quote بگذارید. برای تست databaseName=cbs_test را تکرار کنید. credential در properties/environment باشد و نه داخل URL. در PowerShell classpath را با `Get-Content -Raw /tmp/cbs-jdbc-classpath` بخوانید و Java را با `&` اجرا کنید؛ Windows به مسیر موقت محلی خودش نیاز دارد.

SELECT موفق فقط اتصال را ثابت می‌کند. CBS08 هنوز profile از نوع Maven Failsafe با نام sqlserver-it، تعداد تست واقعی مثبت، fail شدن بدون DB و شاهد rollback/fixture commit‌شده می‌خواهد. profile حذف‌کنندهٔ persistence به نام smoke برای آن تست‌ها مجاز نیست. migration تجاری CBS09 همچنان مرور قید/تراکنش را می‌خواهد.

## توقف، حفظ داده و reset عمدی

```sh
docker compose --env-file .env.sqlserver stop sqlserver
docker compose --env-file .env.sqlserver start sqlserver
# Remove the container/network while preserving its named data volume:
docker compose --env-file .env.sqlserver down
```

reset مخرب، فقط وقتی آگاهانه دادهٔ هر دو دیتابیس توسعه/تست را دور می‌ریزید:

```sh
docker compose --env-file .env.sqlserver down --volumes
```

تغییر متغیر محیطی sa، روش rotation رمز دادهٔ حفظ‌شده نیست. cleanup خودکار یا حذف volume بخشی از این کار نیست.

## شواهد لازم و بررسی فعلی

digest، معماری host/daemon، build/edition دقیق سرور، compatibility/collation، تنظیم RCSI/snapshot، encryption، مجوز login اختصاصی و نسخهٔ JDBC/نتیجهٔ SELECT را ثبت کنید؛ بدون credential. pin نسخه و validation تنظیمات، شاهد runtime موفق نیست.

بررسی فعلی: client/engine از نوع Docker 28.2.2، Desktop 4.42.0، daemon از نوع linux/arm64 و Compose 2.36.2؛ manifest مایکروسافت تأیید شد، Compose با placeholder غیرراز و بدون اجرای کانتینر validate شد، syntax shell بررسی و probe با Java 21 compile شد. آزمایش مجاز بعدی startup سالم، metadata سرور، JDBC هر دو دیتابیس و بقا در یک restart کانتینر را بررسی کرد؛ جزئیات و محدودیت در [شواهد setup](SQL-server-evidence.md) است. جدول مالی یا موجودی مقداردهی نشده است. پذیرش مستقل CBS00 تغییر نکرده است.

محیط روی میزبان x86-64 منتخب هم قابل‌استفاده است؛ فرض loopback/TLS آن میزبان را جدا بررسی کنید و پورت 1433 را عمومی باز نکنید. [نصب بومی Windows](Windows-sqlserver.md) اختیاری می‌ماند.

## harness خودکار CBS08

[راهنمای تست SQL Server](SQL-server-tests.md) اکنون profile نوع Failsafe با نام sqlserver-it، متغیرهای تست، fixture موقت و محافظ‌های خطا را توضیح می‌دهد. probe مستقل CBS07 برای عیب‌یابی مفید می‌ماند و harness خودکار نیست.

## نبودن تنظیمات اتصال در محیط اجرا

اگر startup پیام `SQLServerDriver claims to not accept jdbcUrl, ${DB_URL}` می‌دهد، پردازش برنامه مقدار `DB_URL` را دریافت نکرده است. خطای بعدی dialect در Hibernate پیامد همان URL نامعتبر است. متغیرهای `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` را در محیط پردازشی که Spring Boot را اجرا می‌کند تنظیم کنید؛ برای اجرای IDE، این مقادیر باید در run configuration همان اجرا باشند. فایل ignored به نام `.env.sqlserver` منبع محلی این مقادیر است و Spring Boot آن را خودکار نمی‌خواند. گزینهٔ `--env-file` در Docker Compose هم فقط برای Compose است و به JVM جداگانه منتقل نمی‌شود. credential را داخل JDBC URL نگذارید و برای پوشاندن خطا، H2 یا dialect دستی اضافه نکنید. startup عادی همچنان SQL Server تنظیم‌شده و schema اعمال‌شده می‌خواهد.
