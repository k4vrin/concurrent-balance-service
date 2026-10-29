> راه‌اندازی فعلی: برای API محلی و درخواست‌های اجرایی، [walkthrough کامل HTTP](HTTP-samples.md) را دنبال کنید. [هستهٔ CBS20](Core-acceptance.md) کامل است؛ توضیح‌های تاریخ‌دار زیر، checkpoint اولیهٔ خود را شرح می‌دهند.

> به‌روزرسانی persistence: [شواهد CBS10](Persistence.md) اکنون نگاشت JPA، fixture افتتاحیهٔ committed و route موجودی SQL Server را تأیید می‌کند. mutation هنوز unavailable است. گفته‌های قبلی دربارهٔ 503 برای همهٔ routeها و شواهد فقط schema در ادامه تاریخی‌اند.

# schema در CBS09 و راهنمای migration در SQL Server

[English](../en/Schema.md) · [قرارداد](CBS09-contract.md) · [SQL Server محلی](Docker.md) · [harness تست](SQL-server-tests.md)

## دامنهٔ پیاده‌شده

فایل [V1__create_balance_schema.sql](../../src/main/resources/db/migration/V1__create_balance_schema.sql) اکنون accounts، operations، journals و postings را در dbo می‌سازد. Flyway 12.4.0 آن را در cbs_dev و cbs_test محلی اعمال و validate کرده است. این migration، handler مالی، journal افتتاحیه، نگاشت entity یا replay ماندگار پیاده نمی‌کند. fallback در HTTP همچنان 503 می‌دهد.

| قاعدهٔ ذخیره | اجرای migration |
| --- | --- |
| هویت/ارز/دستهٔ حساب | کلید اصلی UUID، کلید کاندید ارز، currency سه حرف بزرگ Unicode و allowlist دسته/status |
| نمایش موجودی | BIGINT؛ مشتری نامنفی؛ funding مصنوعی با مرز علامت‌دار؛ مقدار پیش‌فرض صفر |
| هویت سراسری درخواست | uq_operations_transaction_id با نام مشخص، قید ساختار/طول Unicode BIN2 تست‌شده، بدون تفکیک بر اساس نوع عملیات |
| intent ثبت‌شده | شکل صریح عملیات، مبلغ مثبت BIGINT، نسخه و بایت کامل canonical با سازگاری SHA-256 |
| سیاست و انتخاب | نسخهٔ غیرخالی سیاست اجرا؛ metadata کامل یا غایب funding؛ انتخاب اجباری در نوع‌های موفق تأمین‌شده |
| ردیف پاسخ ذخیره‌شده | بررسی صریح nullability در PENDING/SUCCEEDED/REJECTED و reason/status/content-type/version/body/completion |
| هویت journal | کلید اصلی UUID، operation یکتا و FK سازگار ارز |
| هویت posting | کلید اصلی journal/line؛ خط و مبلغ مثبت؛ side مجاز؛ FK سازگار ارز برای journal/account |
| حذف history | FK با NO ACTION؛ runtime مجوز تغییر/حذف history ندارد |

شناسهٔ حساب درخواستی و provenance تأمین در operations عمداً FK حساب ندارند؛ رد حساب غایب ثبت می‌شود و رزرو پیش از ترتیب قفل حساب، قفل ضمنی نمی‌گیرد. posting مرجع واقعی می‌خواهد. مبلغ اصلی operation می‌تواند از سقف سیاست بیشتر باشد تا رد ماندگار سقف قابل‌ذخیره بماند. evaluator آینده سقف دقیق سیاست را اجرا می‌کند و قید ردیف بازهٔ ساختاری را حفظ می‌کند.

migration، seed مالی ندارد. CHECK معمولی ردیف، توازن مجموع postingها، کامل‌بودن journal موفق، commit نشدن PENDING، تغییرناپذیری نتیجهٔ نهایی یا برابری projection را اجرا نمی‌کند. این‌ها مسئولیت پروتکل تراکنش کنترل‌شده و reconciliation هستند. تست عمداً شکل ذخیرهٔ ردیف را در تراکنش rollback‌شونده بررسی می‌کند و نمونهٔ عملیات مالی نیست.

## هویت‌های دیتابیس

| هویت | اختیار |
| --- | --- |
| sa محلی | فقط provisioning زیرساخت و grant؛ در تست Maven و startup برنامه وجود ندارد |
| cbs_migrator | CONNECT/CREATE TABLE و ALTER/REFERENCES/VIEW DEFINITION روی dbo؛ مالک schema تاریخچهٔ cbs_migrations؛ بدون sysadmin/db_owner یا grant کلی DML جدول‌های کسب‌وکار |
| cbs_app | SELECT چهار جدول؛ INSERT در operations/journals/postings؛ UPDATE فقط balance حساب و ستون‌های انتخاب/پایان عملیات |
| cbs_initializer | SELECT/INSERT حساب برای افتتاح کنترل‌شدهٔ صفر؛ بدون نوشتن history مالی یا DDL |

تاریخچهٔ Flyway در cbs_migrations و با مالکیت cbs_migrator قرار می‌گیرد؛ جدول کسب‌وکار در dbo می‌ماند. این جزئیات، اختیار جدا در ADR-010 را دقیق‌تر می‌کند؛ برای نگه‌داری history لازم نیست DML کلی روی schema کسب‌وکار بدهیم. runtime به‌دلیل نداشتن grant، history را نمی‌خواند. نقش db_datawriter یا UPDATE کلی جدول اضافه نمی‌شود. setup مجوز غیرمنتظرهٔ قبلی را بی‌صدا حذف نمی‌کند؛ تست مجوز باید برای هویت محلی موردنظر موفق باشد.

مجوز DML توازن journal را اجرا نمی‌کند و جلوی نویسندهٔ runtime مخرب را برای ذخیرهٔ PENDING یا تغییر نتیجهٔ نهایی از ستون مجاز نمی‌گیرد. مسیر واحد posting مورداعتماد باید این قواعد را پیاده کند. initializer هم برای ساخت حساب صفر مورداعتماد است؛ موجودی غیرصفر مسیر journal افتتاحیهٔ CBS10 را می‌خواهد.

## provisioning و migration از Bash

ابتدا محیط و bootstrap در CBS07 کامل باشد. JDK 21 و secretهای متفاوت برنامه، migration، initializer و مدیر لازم‌اند. دو secret تازه همان قاعدهٔ ۱۶ تا ۱۲۸ کاراکتر ASCII شامل حرف/رقم/_/- و وجود حرف بزرگ و کوچک و رقم را دارند. provisioning تکرارپذیر است و رمز قبلی را بررسی می‌کند، نه اینکه بی‌صدا عوض کند. secretهای تولیدشدهٔ این جلسه در .env.sqlserver با mode برابر 0600 و ignored هستند؛ مقدار secret وارد Git نشده است.

secret تازه را بدون ثبت در history بخوانیم. wrapper میزبان آن‌ها را از stdin به کانتینر می‌فرستد، نه از آرگومان docker:

```bash
read -r -s -p 'Migration password: ' CBS_MIGRATOR_PASSWORD
export CBS_MIGRATOR_PASSWORD
read -r -s -p 'Initializer password: ' CBS_INITIALIZER_PASSWORD
export CBS_INITIALIZER_PASSWORD
bash scripts/sqlserver/provision-migration-local.sh
export MIGRATION_DB_USERNAME=cbs_migrator
export MIGRATION_DB_PASSWORD="$CBS_MIGRATOR_PASSWORD"
for migration_database in cbs_dev cbs_test; do
  export MIGRATION_DB_URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=$migration_database;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
  ./mvnw -Pdb-migrate flyway:migrate flyway:validate || break
done
```

پیش از grant باید اجرای هر دو دیتابیس موفق باشد:

```bash
bash scripts/sqlserver/grant-runtime-local.sh
unset MIGRATION_DB_URL MIGRATION_DB_USERNAME MIGRATION_DB_PASSWORD
```

profile با نام db-migrate نسخهٔ plugin را از نسخهٔ Flyway مدیریت‌شدهٔ Boot می‌گیرد و وابستگی SQL Server/JDBC را به plugin می‌دهد. فقط MIGRATION_DB_URL/USERNAME/PASSWORD صریح را می‌خواند؛ fallback به DB_* ندارد. createSchemas=false، baselineOnMigrate=false و cleanDisabled=true هستند و نام فایل migration بررسی می‌شود. نبود تنظیمات Maven را شکست می‌دهد. رمز را با Maven -D، لاگ debug یا effective-POM شامل secret حل‌شده منتشر نکنیم.

startup عادی اکنون spring.flyway.enabled=false و Hibernate ddl-auto=validate دارد. schema جدا اعمال و validate می‌شود؛ سپس برنامه فقط با DB_* مربوط به runtime اجرا می‌شود. credential مربوط به migration وارد محیط runtime نمی‌شود. وابستگی SQL Server همچنان پیش‌فرض است و profile زمان اجرای SQL Server لازم نیست.

## اجرای تست واقعی

هر سه اتصال تست جدا و صریح تنظیم شوند؛ همه باید به cbs_test وصل شوند. کپی URL تست صریح در این مثال، fallback به DB_* ایجاد نمی‌کند:

```bash
export SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000'
export SQLSERVER_TEST_USERNAME=cbs_app
read -r -s -p 'Runtime password: ' SQLSERVER_TEST_PASSWORD
export SQLSERVER_TEST_PASSWORD
export SQLSERVER_MIGRATION_TEST_URL="$SQLSERVER_TEST_URL"
export SQLSERVER_MIGRATION_TEST_USERNAME=cbs_migrator
export SQLSERVER_MIGRATION_TEST_PASSWORD="$CBS_MIGRATOR_PASSWORD"
export SQLSERVER_INITIALIZER_TEST_URL="$SQLSERVER_TEST_URL"
export SQLSERVER_INITIALIZER_TEST_USERNAME=cbs_initializer
export SQLSERVER_INITIALIZER_TEST_PASSWORD="$CBS_INITIALIZER_PASSWORD"
./mvnw -Psqlserver-it verify
unset CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD SQLSERVER_TEST_PASSWORD
unset SQLSERVER_MIGRATION_TEST_PASSWORD SQLSERVER_INITIALIZER_TEST_PASSWORD
```

در PowerShell همان نام‌های محیطی با `$env:NAME`، ورودی Read-Host -AsSecureString و تبدیل NetworkCredential طبق [راهنمای تست](SQL-server-tests.md) استفاده شوند. فرمان `.\mvnw.cmd -Pdb-migrate flyway:migrate flyway:validate` برای هر URL صریح migration یک بار اجرا شود. wrapperهای provisioning به Bash نیاز دارند و در ترمینال Bash میزبان Docker اجرا می‌شوند. wrapper بومی PowerShell تست نشده است.

[SqlServerMigrationIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerMigrationIT.java) با datasource محافظت‌شدهٔ migration، V1 را validate و دوباره اجرا می‌کند. جدول/index/FK معتبر و بدون cascade، شکست قید با نام مشخص، رد NULL لازم، شکل معتبر ردیف و الزام funding، یکتایی حساس به حروف، ذخیرهٔ بالاتر از سقف، مرز علامت‌دار، rollback و مجوز runtime را بررسی می‌کند. پنجاه مورد آن به ۴۴ تست واقعی قبلی اضافه می‌شوند. تنظیم migration/initializer اجباری است و در نبود آن تست skip نمی‌شود. کلاس دقیقاً سه fixture پایدار حساب با موجودی صفر را در cbs_test از مسیر cbs_initializer می‌سازد. همهٔ probeهای operation/journal/posting و موجودی غیرصفر rollback می‌شوند. این ردیف‌ها جای journal افتتاحیهٔ CBS10 را نمی‌گیرند.

## شواهد ثبت‌شده، ۲۰۲۶-۱۰-۲۳

- دیتابیس تست پیش از نخستین migration صفر جدول کاربر داشت. cbs_migrator اختصاصی V1 و validate را در cbs_dev/cbs_test اجرا کرد؛ هر دو history نسخهٔ ۱، success برابر ۱ و checksum برابر 982769180 دارند. اجرای dev با setup پلاگین ۸٫۸۸۶ ثانیه و تست ۳٫۵۸۳ ثانیه طول کشید. این‌ها مشاهده‌اند و هدف performance نیستند.
- اجرای نهایی کامل Java 21.0.11 در ۱۷٫۸۷۹ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۹۴ تست واقعی SQL Server را بدون failure، error یا skip گذراند. پنجاه مورد schema برابر ۲٫۵۸۵ ثانیه طول کشیدند. XML تازه بررسی شد.
- نبود تنظیم migration، Maven را با خطای تنظیمات موردانتظار شکست داد. migrate با cbs_app خطای مجوز SQL برابر 262 داد. Flyway clean رد می‌شود. grant دوباره موفق بود.
- JAR برنامه فقط با cbs_app و بدون credential مربوط به migration روی پورت موقت loopback به health برابر UP رسید و سپس متوقف شد.
- شمارش نهایی dev برای هر چهار جدول صفر است. تست ۳ حساب صفر و صفر operation/journal/posting دارد. fixture مالی غیرصفر یا نامتوازن commit نشد. secretهای محلی ignored ماندند و لاگ/گزارش برای وجودشان بررسی شد.

شواهد همچنان مربوط به آزمایش مجاز و پشتیبانی‌نشدهٔ SQL Server 2022 CU22 Developer زیر emulation روی ARM64 است. اجرای واقعی روی دیتابیس خالی در نخستین اجرای محلی ثبت شد؛ تست خودکار بعدی schema حفظ‌شده را validate/rerun می‌کند و clean/drop ندارد. V1 اعمال‌شده تغییرناپذیر است؛ تغییر آینده با V2 انجام می‌شود، نه ویرایش یا repair کردن checksum آن. پیاده‌سازی CBS09 تأیید شده و پذیرش رسمی یادگیری جداست. گام بعد CBS10، نگاشت‌ها، fixture افتتاحیهٔ journal-backed و خواندن موجودی committed است.
