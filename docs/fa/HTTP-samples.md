# اجرای محلی نمونه‌های HTTP

[English](../en/HTTP-samples.md) · [README](../../README.fa.md) · [فایل درخواست](../../http/balance-service.http) · [قرارداد API](API-v1.md)

نام فایل اجرایی، **HTTP Client request file** با پسوند `.http` است. collection از syntax مشترک `@variable = value`، `{{variable}}` و `###` استفاده می‌کند که در [IntelliJ IDEA HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) و [VS Code REST Client](https://github.com/Huachao/vscode-restclient) مستند شده است. اسکریپت مخصوص editor، شناسهٔ تصادفی درخواست یا credential در فایل نداریم.

## ۱. آماده‌سازی SQL Server

فرمان‌ها را در **Bash و از ریشهٔ مخزن** اجرا کنید و JDK 21 را انتخاب کرده باشید. `.env.example` الگو است؛ اگر `.env.sqlserver` وجود دارد، همان فایل را ویرایش کنید و روی آن کپی نکنید.

```bash
cp .env.example .env.sqlserver
chmod 600 .env.sqlserver
```

فایل نادیده‌گرفته‌شده را محلی ویرایش کنید. پس از پذیرش مجوز SQL Server Developer، مقدار `ACCEPT_EULA=Y` بگذارید و چهار secret مجزا انتخاب کنید: `MSSQL_SA_PASSWORD`، `CBS_APP_PASSWORD`، `CBS_MIGRATOR_PASSWORD` و `CBS_INITIALIZER_PASSWORD`. رمز سه login محدود باید ۱۶ تا ۱۲۸ کاراکتر ASCII از حروف/رقم/underscore/hyphen، شامل حرف بزرگ و کوچک و رقم باشد. فرمان‌های زیر port 14333 دارند؛ اگر `SQLSERVER_PORT` را عوض کردید، همه‌جا هماهنگ تغییر دهید.

image پین‌شده به منابع Docker نیاز دارد؛ emulation روی ARM64 آزمایش محلی بدون پشتیبانی است. جزئیات پلتفرم و مجوز در [راهنمای Docker](Docker.md) آمده است. راه‌اندازی و bootstrap:

```bash
docker compose --env-file .env.sqlserver config --quiet
docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver
docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh
```

فایل env ورودی Compose است و shell script نیست. آن را **`source` نکنید**؛ URLهای JDBC کاراکترهای ویژهٔ shell دارند و Spring Boot هم فایل را خودکار نمی‌خواند. در prompt، همان رمز migration و initializer نوشته‌شده در فایل را وارد کنید:

```bash
read -r -s -p 'Migration password: ' CBS_MIGRATOR_PASSWORD; printf '\n'
read -r -s -p 'Initializer password: ' CBS_INITIALIZER_PASSWORD; printf '\n'
export CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD
bash scripts/sqlserver/provision-migration-local.sh
export MIGRATION_DB_USERNAME=cbs_migrator
export MIGRATION_DB_PASSWORD="$CBS_MIGRATOR_PASSWORD"
for migration_database in cbs_dev cbs_test; do
  export MIGRATION_DB_URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=$migration_database;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
  ./mvnw -Pdb-migrate flyway:migrate flyway:validate || break
done
```

پیش از ادامه، اجرای موفق **هر دو** catalog را بررسی کنید. سپس grantهای runtime را اعمال کنید:

```bash
bash scripts/sqlserver/grant-runtime-local.sh
unset MIGRATION_DB_URL MIGRATION_DB_USERNAME MIGRATION_DB_PASSWORD CBS_MIGRATOR_PASSWORD
```

این مراحل داده‌های موجود را نگه می‌دارند و رمز login قبلی را عوض نمی‌کنند. اگر setup شکست خورد، [Schema.md](Schema.md) را ببینید؛ volume را برای پنهان‌کردن ناسازگاری رمز یا schema حذف نکنید.

## ۲. ساخت حساب‌های نمونه با موجودی صفر

فایل [create-demo-accounts.sql](../../http/create-demo-accounts.sql) را با `cbs_initializer` روی **cbs_dev** اجرا کنید. catalog دیگر یا identity مدیر/owner را نمی‌پذیرد. فرمان زیر secret و SQL را از stdin می‌فرستد و secret را در argument فرمان نمی‌گذارد:

```bash
{
  printf '%s\n' "$CBS_INITIALIZER_PASSWORD"
  cat http/create-demo-accounts.sql
} | docker compose --env-file .env.sqlserver exec -T sqlserver bash -c \
  'read -r SQLCMDPASSWORD; export SQLCMDPASSWORD; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U cbs_initializer -d cbs_dev -C -b'
unset CBS_INITIALIZER_PASSWORD
```

| نقش | UUID |
| --- | --- |
| Funding | `90000000-0000-4000-8000-000000000001` |
| Alice | `10000000-0000-4000-8000-000000000001` |
| Bob | `20000000-0000-4000-8000-000000000002` |

اجرای دوباره فقط حساب‌های ناموجود را اضافه می‌کند، category/currency/status حساب موجود را می‌سنجد و موجودی/history را تغییر نمی‌دهد. seed غیرصفر SQL و endpoint عمومی ساخت حساب نداریم؛ پول فقط از credit در HTTP وارد می‌شود. fixture برای یک اپراتور محلی است؛ چند نسخهٔ provisioning را هم‌زمان اجرا نکنید.

## ۳. راه‌اندازی برنامه

در همان Bash، رمز runtime مطابق `CBS_APP_PASSWORD` فایل محلی را وارد کنید:

```bash
export DB_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_dev;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000'
export DB_USERNAME=cbs_app
read -r -s -p 'Runtime password: ' DB_PASSWORD; printf '\n'
export DB_PASSWORD
export EXECUTION_POLICY_ENABLED=true
export EXECUTION_IRR_FUNDING_ACCOUNT_ID=90000000-0000-4000-8000-000000000001
export EXECUTION_IRR_FUNDING_POLICY_VERSION=demo-funding-v1
./mvnw spring-boot:run
```

مراحل بالا secret مربوط به migration/initializer را از محیط runtime حذف می‌کنند. اگر shell از قبل secret مدیر یا provisioning/test دیگری را export کرده است، پیش از راه‌اندازی آن‌ها را هم حذف کنید. در IDE همین شش متغیر را در run configuration برنامه قرار دهید؛ credential مهاجرت آنجا نباشد. URL در shell باید quote شود، چون semicolon دارد. آدرس پیش‌فرض `http://127.0.0.1:8080` است؛ در صورت نیاز `SERVER_PORT` و `baseUrl` فایل درخواست را با هم تغییر دهید.

## ۴. اجرای فایل درخواست

فایل [http/balance-service.http](../../http/balance-service.http) را باز کنید:

- **IntelliJ IDEA:** پشتیبانی HTTP Client را فعال کنید و دکمهٔ اجرا کنار هر درخواست را بزنید.
- **VS Code:** افزونهٔ REST Client را نصب/فعال کنید و **Send Request** بالای درخواست را انتخاب کنید.

درخواست‌های شماره‌دار را به‌ترتیب و بدون writer هم‌زمان روی حساب‌های نمونه اجرا کنید. هر دو client از متغیرهای خود فایل استفاده می‌کنند و به `http-client.env.json` نیاز ندارند. مقدار `runId=demo-v1` عمداً ثابت است؛ برای retry، restart و نمایش replay آن را عوض نکنید. مقدار تصادفی در هر درخواست، عملیات تازه می‌سازد.

## نتیجهٔ موردانتظار

جدول زیر حساب‌های تازه با موجودی صفر، سقف پیش‌فرض IRR و ترتیب دقیق درخواست‌ها را فرض می‌کند. status/code موردانتظار کنار هر درخواست هم نوشته شده است.

| درخواست‌ها | نتیجه | Alice / Bob / funding به ریال |
| --- | --- | --- |
| 01–02 | health با 200/UP؛ موجودی Alice صفر. | 0 / 0 / 0 |
| 03–05 | credit هزار ریالی Alice، replay بدون اثر تازه، credit پانصد ریالی Bob. | 1000 / 500 / 1500 |
| 06 | debit صد ریالی Alice. | 900 / 500 / 1400 |
| 07–11 | انتقال ۲۵۰، replay، رد مبلغ متفاوت با همان ID و استعلام دو حساب. | 650 / 750 / 1400 |
| 12 | debit مبلغ ۶۵۱ با 409 `INSUFFICIENT_FUNDS`؛ بدون journal. | 650 / 750 / 1400 |
| 13–16 | top-up صد ریالی Alice؛ debit ردشده همچنان 409 اصلی را replay می‌کند. | 750 / 750 / 1500 |
| 17–20 | مبلغ صفر/اعشاری، self-transfer و UUID نامعتبر پاسخ 400 دارند. | بدون تغییر |
| 21–23 | lookup/mutation حساب ناموجود 404؛ credit بالاتر از سقف، 409 `TRANSACTION_LIMIT_EXCEEDED`. | بدون تغییر |

پس از همهٔ درخواست‌ها، **۸ operation ماندگار نمونه** داریم: ۵ موفقیت، ۳ رد کسب‌وکار، ۵ journal و ۱۰ posting. lookup ناموجود، validation، conflict و replay رکورد operation اضافه نمی‌کنند. مثال حساب ناموجود فرض می‌کند UUID متغیر `missing` ساخته نشده است. funding با credit زیاد و با debit کم می‌شود؛ projection برابر ۱۵۰۰ آن، طرف مقابل مجموع بدهی به دو مشتری است.

اجرای دوبارهٔ collection حساب‌ها را reset نمی‌کند؛ موجودی میانی به state نهایی قبلی وابسته می‌شود. همان IDها پاسخ اصلی را می‌دهند، حتی برای رد کمبود موجودی. برای نمایش کاملاً تازه، UUID مشتری/funding را در **هر دو فایل SQL و `.http`** عوض کنید، funding سرور را هماهنگ کنید و `runId` تازه بگذارید. تغییر `runId` به‌تنهایی تلاش مالی تازه روی موجودی قبلی می‌سازد و نتیجهٔ سناریو را تغییر می‌دهد.

برای نمایش replay پس از restart، برنامه را با همان تنظیمات متوقف/دوباره اجرا کنید و درخواست‌های 04، 08 و 14 را بفرستید. موجودی درخواست‌های 15–16 باید ثابت بماند. شواهد خودکار rollback/restart/concurrency در [پذیرش هسته](Core-acceptance.md) آمده‌اند؛ این درخواست‌های ترتیبی، درستی هم‌زمانی را ثابت نمی‌کنند.

## عیب‌یابی

| نشانه | بررسی |
| --- | --- |
| Connection refused | برنامه را راه‌اندازی و port loopback و `baseUrl` را بررسی کنید. |
| مقدار literal `${DB_URL}` یا خطای datasource | متغیرهای runtime را در process راه‌انداز export کنید؛ فایل env به‌تنهایی کافی نیست. |
| 503 همهٔ mutationها | execution، UUID/version مربوط به funding، schema و اتصال دیتابیس را بررسی کنید. برای بازیابی نتیجهٔ نامعلوم همان ID را نگه دارید. |
| 404 برای Alice/Bob | fixture را در `cbs_dev` اجرا کنید و catalog runtime و UUIDها را تطبیق دهید. |
| موجودی غیرمنتظره | history قبلی، `runId` متفاوت، ترتیب درخواست یا writer دیگر؛ با UPDATE مستقیم موجودی آن را اصلاح نکنید. |
| 409 برای درخواست تغییریافته | ID قبلی ماندگار است؛ همان درخواست را replay کنید و فقط برای عملیات تازه ID تازه بگذارید. |

برنامهٔ میزبان را با `Ctrl+C` متوقف کنید. فرمان `docker compose --env-file .env.sqlserver down` کانتینر/network را حذف و volume دیتابیس را نگه می‌دارد. فقط اگر عمداً داده‌ها را دور می‌ریزید، `--volumes` اضافه کنید.

## بررسی اندازه‌گیری‌شدهٔ نمونه‌ها، ۲۰۲۶-۱۰-۲۹

هر ۲۳ درخواست به‌ترتیب روی برنامهٔ بسته‌بندی‌شده و catalog واقعی `cbs_dev` در SQL Server محلی اجرا شدند. همهٔ status/codeها و موجودی‌های میانی مطابق انتظار بودند و body در replay یکسان ماند. پس از restart process برنامه، replay موفقیت credit/transfer و رد، و موجودی نهایی تغییر نکردند. SQL مستقل، ۸ operation، ۵ موفقیت، ۳ رد، ۵ journal، ۱۰ posting و برابری projection/history را تأیید کرد. اجرای دوبارهٔ fixture حساب‌ها را نگه داشت؛ اجرای catalog اشتباه یا identity مربوط به runtime رد شد. verifier موقت، درخواست‌ها را از همین فایل `.http` خواند؛ اجرای UI خود editor اندازه‌گیری نشد. Maven قابل‌حمل هم ۴۲۱ تست را با صفر failure/error/skip گذراند.
