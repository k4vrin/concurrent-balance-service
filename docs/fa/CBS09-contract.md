> به‌روزرسانی پیاده‌سازی CBS09 در ۲۰۲۶-۱۰-۲۳: V1 در دیتابیس محلی توسعه/تست اعمال و validate شده است. [schema و راهنما](Schema.md) قید، grant و شواهد ۲۴۰ تست مستقل از زیرساخت و ۹۴ تست SQL Server را ثبت می‌کند. گفته‌های تاریخی پیش از migration در ادامه با این شواهد جایگزین می‌شوند. اجرای مالی هنوز پیاده نشده است.

# قرارداد پیاده‌سازی CBS09

[English](../en/CBS09-contract.md) · [دفترکل](Ledger.md) · [ذخیرهٔ هویت](Transaction-id-storage.md) · [ADR-010](../adr/ADR-010-persistence-contract.fa.md)

## نتیجهٔ مرور، ۲۰۲۶-۱۰-۲۳

مرور مهندسی CBS02–04 برای شروع CBS09 کامل است. این قرارداد انتخاب‌های فنی باز را مشخص می‌کند؛ پذیرش تک‌تک جزئیات توسط مالک پروژه یا تسلط یادگیرنده را ادعا نمی‌کند. تصمیم‌های پذیرفته‌شدهٔ کسب‌وکار، مرجع دامنهٔ چهار عملیات، ledger، نگه‌داری هویت، دستهٔ مشتری و funding و سقف 10000000000000 ریالی می‌مانند.

CBS09 اکنون پیاده‌سازی و تست migration این قرارداد است. رفتار کامل مالی و شواهد خرابی آن در CBS10–20 انجام می‌شوند و پیش‌نیاز ساخت schema نیستند. این مرور DDL کسب‌وکار اعمال نکرده است.

## ذخیره‌سازی و قیدهای انتخاب‌شده

SQL Server 2022 CU22، schema برابر `dbo`، مبلغ BIGINT ریالی، شناسهٔ حساب/عملیات/journal از نوع `uniqueidentifier`، زمان UTC از نوع `datetime2(7)` و collation باینری صریح برای کدهای نمادین به کار می‌روند. برنامه UUID را یک بار برای تلاش منطقی تولید می‌کند؛ recovery نامطمئن همان شناسه‌ها را حفظ می‌کند. transaction ID عمومی همچنان رشتهٔ opaque معتبر است.

| جدول | فیلدهای لازم و قید ردیف |
| --- | --- |
| accounts | کلید اصلی UUID به نام account_id؛ category با CUSTOMER_LIABILITY یا FUNDING_CLEARING؛ currency سه حرف بزرگ؛ balance از نوع BIGINT و NOT NULL؛ status با ACTIVE یا CLOSED؛ created_at در UTC. موجودی مشتری نامنفی است؛ funding مصنوعی کل signed-long را می‌پذیرد. حساب تازه از مسیر کنترل‌شده با صفر شروع می‌شود. category/currency/identity در نوشتن runtime تغییرناپذیرند. |
| operations | کلید اصلی UUID به نام operation_id؛ transaction_id یکتای سراسری با nvarchar(256)/BIN2 و قید طول/کاراکتر تست‌شده؛ operation_type شامل CREDIT/DEBIT/TRANSFER/OPENING؛ UUID مبدأ و مقصد درخواستی؛ amount مثبت BIGINT؛ currency؛ نسخه، بایت کامل و digest از نوع SHA-256 برای canonical؛ نسخهٔ سیاست اجرا؛ شناسه/نسخه/digest اختیاری funding ذخیره‌شده؛ state با PENDING/SUCCEEDED/REJECTED؛ reason و envelope پاسخ nullable؛ زمان ایجاد/پایان UTC. |
| journals | کلید اصلی journal_id از نوع UUID؛ operation_id اجباری و یکتا؛ currency؛ created_at. دقیقاً یک header برای mutation موفق، invariant تراکنش برنامه است. |
| postings | کلید اصلی journal_id/line_number با line_number مثبت؛ UUID حساب و journal و currency اجباری؛ side با DEBIT/CREDIT؛ amount مثبت BIGINT. شناسهٔ تغییرپذیر جدا یا cascade delete لازم نیست. |

شکل operation صریح است؛ CREDIT/OPENING مقصد اجباری و مبدأ ممنوع دارند؛ DEBIT مبدأ اجباری و مقصد ممنوع دارد؛ TRANSFER هر دو را با مقدار متفاوت می‌خواهد. شاخه‌های IS NULL/IS NOT NULL را صریح بنویسیم تا unknown در SQL قید شکل را دور نزند. شناسهٔ حساب درخواستی و provenance مربوط به funding در operations، FK حساب ندارند؛ رد حساب غایب باید ثبت شود و رزرو نباید پیش از مرحلهٔ مرتب‌شدهٔ قفل، از مسیر FK قفل ضمنی حساب بگیرد.

قید `uq_operations_transaction_id` فقط روی transaction_id است. کلیدهای کاندید account_id/currency، operation_id/currency و journal_id/currency برای FK ترکیبی ارز اضافه می‌شوند. journal با operation_id/currency به operations وصل می‌شود؛ posting با currency به journal و account وصل می‌شود. قیدها نام پایدار و روشن و حذف از نوع NO ACTION دارند. هزینهٔ indexهای کاندید اضافه برای درستی ارز است، نه index تکراری performance. index حساب/ارز در postings برای دسترسی به مرجع حساب و reconciliation اضافه می‌شود. index فرضی history/status یا index دیگر برای transaction ID نسازیم.

canonical bytes مرجع‌اند و digest همراه آن‌هاست. نسخهٔ قالب ۱، بایت غیرخالی varbinary(max) و SHA-256 از نوع binary(32) ذخیره شود. سازگاری digest با HASHBYTES در تست migration بررسی شود. metadata تأمین یا کاملاً غایب است یا کامل؛ transfer آن را ندارد؛ CREDIT/DEBIT/OPENING موفق آن را می‌خواهند. یک snapshot تغییرناپذیر سیاست و funding سرور در retry کل تلاش حفظ و در پایان ثبت شود؛ replay انتخاب فعلی را دوباره اجرا نمی‌کند. نسخهٔ ذخیره‌شدهٔ ناشناخته خطا می‌دهد و عملیات تازه تلقی نمی‌شود.

PENDING فاقد زمان پایان، reason و پاسخ است. SUCCEEDED زمان پایان و پاسخ می‌خواهد و reason ندارد. REJECTED زمان پایان، پاسخ و reason شناخته‌شده می‌خواهد. پاسخ نهایی نسخهٔ ۱، HTTP status، بایت دقیق UTF-8 و content type را حفظ می‌کند؛ موفقیت 200 و application/json دارد و رد از 4xx انتخاب‌شده و application/problem+json استفاده می‌کند. OPENING داخلی envelope نتیجهٔ داخلی را حفظ می‌کند و route عمومی اضافه نمی‌کند. مبلغ canonical اصلی می‌تواند بالاتر از سقف کسب‌وکار باشد تا رد سقف قابل‌ذخیره بماند. TRANSACTION_LIMIT_EXCEEDED و BALANCE_LIMIT_EXCEEDED هنگام پیاده‌سازی evaluator و نگاشت HTTP به reasonهای قبلی اضافه می‌شوند.

سقف کسب‌وکار بررسی سیاست است و در Money عمومی یا CHECK مبلغ operations ثابت نمی‌شود. برای عملیات تازه، سقف 10000000000000 ریالی مبلغ و موجودی نهایی مشتری با snapshot نسخه‌دار ذخیره‌شده اعمال می‌شود؛ schema نامنفی‌بودن ساختاری و مرز BIGINT را نگه می‌دارد. تغییر سقف آینده، سیاست گذار مستند می‌خواهد. نتیجهٔ نهایی موجود پیش از ارزیابی دوباره replay می‌شود. رد، journal یا projection نمی‌نویسد.

## adapter انتخاب‌شدهٔ قفل

SQL بومی صریح از EntityManager در همان JpaTransactionManager و REQUIRED TransactionTemplate و persistence context تازهٔ هر تلاش اجرا می‌شود. به ترجمهٔ SQL از annotation عمومی pessimistic یا state حساب قبلاً بارگذاری‌شده تکیه نمی‌کنیم. CBS10 باید نگاشت entity و مشارکت تراکنش را روی migration واقعی تأیید کند.

```sql
SELECT account_id, category, currency, balance, status
FROM dbo.accounts WITH (UPDLOCK, HOLDLOCK)
WHERE account_id = CAST(? AS uniqueidentifier);
```

فقط متن canonical UUID حاصل از AccountId را bind کنیم. مجموعهٔ کامل حساب‌های درگیر deduplicate و با AccountLockOrder.DESCENDING مرتب می‌شود؛ برای هر شناسه یک lookup به همان ترتیب انجام می‌شود. ORDER BY در SQL یا query چندشناسه‌ای تضمین ترتیب گرفتن قفل نیست. حساب درخواستی غایب رد نهایی می‌دهد؛ lookup کلید غایب محافظت range را تا پایان حفظ می‌کند. funding داخلی غایب نقص تنظیمات فنی است و rollback می‌شود. محافظت کلید غایب ممکن است gap کامل index را مسدود کند؛ قفل صرفاً ردیفی یا پیشرفت مستقل همگانی ادعا نمی‌شود. همهٔ نویسنده‌های افتتاح و نگه‌داری همان ترتیب را رعایت می‌کنند.

شواهد تازهٔ native JDBC، round trip مربوط به UUID و BIGINT علامت‌دار، تفاوت sort در SQL GUID با comparator برنامه، مسدودشدن نویسنده، پیشرفت کلید موجود دیگر، مسدودشدن ساخت کلید غایب و پایان دو worker جهت مخالف با ترتیب یکسان و بدون تکرار را می‌سنجند. escalation اندازه‌گیری نشده و نبود همهٔ deadlockها اثبات نمی‌شود. [قواعد table hint در Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table) مکانیزم را شرح می‌دهند؛ تست migration و entity واقعی همچنان لازم است.

## قرارداد تراکنش و recovery

coordinator تراکنش محیطی را نمی‌پذیرد. intent و دسترسی اعتبارسنجی شوند؛ هویت موجود پیدا و canonical bytes کامل پیش از ارزیابی سیاست قابل‌تغییر مقایسه شود. operation پیش از قفل حساب reserve/flush شود. duplicate موردانتظار باید از تراکنش خارج و rollback شود و سپس نتیجهٔ committed از مرز تازه بازیابی شود. state حساب قفل‌شده بررسی شود؛ پیش از هر نوشتن پولی، journal کامل متوازن و همهٔ projectionهای checked محاسبه شوند. journal، postings، projection و پاسخ نهایی با هم ذخیره شوند. رد کسب‌وکار مقدار عادی callback است تا ردیف آن commit شود. خطای فنی خارج و rollback می‌شود. موفقیت فقط پس از commit قطعی نمایش داده می‌شود.

توازن journal میان ردیف‌ها، کامل‌بودن journal موفق، تغییرناپذیری نتیجهٔ نهایی و commit نشدن PENDING، invariant برنامه و پروتکل هستند. CHECK عادی ردیف آن‌ها را ثابت نمی‌کند. مستند و تست migration باید همین تفاوت را حفظ کنند. reconciliation تعداد operation/journal، مجموع postingها و برابری projection را با محاسبهٔ دقیق بزرگ‌تر در fixture بدون فعالیت هم‌زمان یا snapshot committed سازگار مستقل بررسی می‌کند.

| خطا | اقدام لازم |
| --- | --- |
| 2627/2601 مربوط به کلید transaction ID با نام مشخص | rollback و recovery تازه؛ canonical intent مقایسه و نتیجهٔ قبلی یا conflict برگردانده شود. کلید یکتای دیگر نقص است و replay نیست. |
| قربانی deadlock با 1205 | پس از پاک‌سازی تراکنش و در بودجه، کل تلاش retry شود؛ یک statement تنها retry نشود. |
| timeout قفل با 1222 | کل تلاش صریح rollback شود؛ سپس recovery/retry در بودجه. تست نشان می‌دهد تراکنش ممکن است باز بماند. |
| CHECK/FK غیرمنتظره، دادهٔ ذخیره‌شدهٔ نامعتبر یا سرریز invariant | rollback و خطای فنی؛ آن را duplicate یا کسری موجودی جلوه ندهیم. |
| قطع اتصال در commit یا پایان نامطمئن | recovery با همان شناسه پیش از اجرای دوباره. یک lookup خالی اثبات commit نشدن نیست. اگر حل نشد، unavailable موقت با همان شناسه برای retry بدهیم. |

تنظیم اولیهٔ محیط محلی انتخاب شد؛ حداکثر ۳ تلاش، READ_COMMITTED، انتظار قفل ۱۰۰۰ میلی‌ثانیه، timeout query برابر ۲ ثانیه، تراکنش ۵ ثانیه، دریافت اتصال datasource برابر ۵ ثانیه و بودجهٔ cooperative درخواست ۲۰ ثانیه. timeout مربوط به login/socket در driver همان ۵ ثانیه است. full jitter نمایی بیرون از تراکنش، ابتدا صفر تا ۵۰ و سپس صفر تا ۱۰۰ میلی‌ثانیه است. تلاش تازه باید در بودجهٔ باقی‌مانده جا شود؛ انتظار اتصال، recovery و backoff هم حساب می‌شوند. timeout query بومی تنظیم و رفتار driver هنگام پیاده‌سازی تأیید شود. deadline مجوز رهاکردن cleanup یا ادعای مرز سخت شبکه/commit نیست. این‌ها مقدار اولیهٔ مهندسی‌اند و SLO تولید اندازه‌گیری‌شده نیستند؛ تنظیم runtime و retry هنوز کد آینده‌اند.

## اختیار migration و runtime

login/user جدا برای migration با CONNECT، CREATE TABLE و ALTER/REFERENCES روی schema برنامه و حق لازم برای history خودش در Flyway استفاده شود. sysadmin/db_owner نباشد. credential مربوط به migration وارد محیط runtime بسته‌بندی‌شده نشود. CBS09 باید migrate/validate واقعی Flyway را با همان هویت اجرا کند، در نبود آن شکست بخورد و clean را غیرفعال نگه دارد. baseline-on-migrate برای پنهان‌کردن schema ناشناخته ممنوع است.

cbs_app در runtime فقط SELECT چهار جدول، INSERT در operations/journals/postings و UPDATE ستون‌های پایان operation و balance حساب می‌گیرد. DDL، UPDATE/DELETE تاریخچه، DELETE عملیات، UPDATE هویت/canonical یا نقش کلی db_datawriter ندارد. ساخت حساب با هویت کنترل‌شدهٔ initializer/maintenance با اختیار محدود INSERT حساب است؛ موجودی غیرصفر همچنان journal افتتاحیه را از مسیر مالی می‌خواهد. endpoint عمومی ساخت/بستن حساب اضافه نمی‌شود. مجوز DML، درج دلخواه را متوازن نمی‌کند و جلوی commit شدن PENDING توسط نویسندهٔ مخرب را نمی‌گیرد. مسیر واحد مورداعتماد posting، نبود نویسندهٔ جایگزین، تست خرابی و reconciliation این invariantها را اجرا می‌کنند. اجبار قوی‌تر با stored procedure تغییر طراحی جدا می‌خواهد.

cbs_app فعلاً CONNECT-only می‌ماند؛ این مرور grant نمی‌دهد. CBS09 هویت‌های جدا را بیرون از SQL نسخه‌دار کسب‌وکار provision و مجوز واقعی را تست می‌کند. تغییر schema با Flyway از startup زمان اجرا جدا می‌شود؛ Hibernate روی ddl-auto=validate می‌ماند. خطای مجوز/DDL باید startup یا deployment را شکست دهد و به schema update خودکار برنگردد. هویت عملیات و شواهد journal/posting نامحدود حفظ می‌شوند؛ cascade یا job حذف نداریم. بستن حساب فعلاً عقب افتاده است و حساب دارای سابقه حذف نمی‌شود. اصلاح با journal تازه انجام می‌شود، نه تغییر history.

## دامنه و معیار خروج CBS09

فایل `V1__create_balance_schema.sql` با چهار جدول و قید/index صریح و بدون seed مالی غیرصفر پیاده شود. اختیار migration/runtime/initializer با setup زیرساخت محلی و secret تولیدشدهٔ ignored ایجاد شود. credential و حالت اجرای Flyway جدا تنظیم شوند. تست واقعی SQL Server برای migration دیتابیس خالی، validate/rerun، metadata و index واقعی، ورودی مستقیم نامعتبر و مرزی، FK ارز، شکل عملیات و state ردیف پاسخ نهایی، یکتایی سراسری، مجوز محدود runtime و rollback لازم است. تست منفی باید به قید با نام موردانتظار بخورد، نه به نبود مجوز. verify مستقل از SQL Server بماند.

fixture افتتاحیهٔ journal-backed، نگاشت JPA و query موجودی مرجع در CBS10 هستند. coordinator و عملیات مالی، replay، نگاشت رد سقف، retry کل تلاش، تزریق خرابی نوشتن، recovery قربانی deadlock، commit نامطمئن و reconciliation مستقل در CBS11–20 هستند. plan و هزینهٔ قید و performance حساب funding داغ در CBS21–22 می‌مانند. ناتمام‌بودن آن‌ها قرارداد schema مرورشده را دوباره باز نمی‌کند؛ شواهد متناقض تازه، اصلاح ثبت‌شده می‌خواهند.

## تأیید اجرا

اجرای کامل Java 21.0.11 با `./mvnw -Psqlserver-it verify` در ۱۶٫۹۹۷ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۴۴ تست واقعی SQL Server را بدون failure، error یا skip گذراند. چهار مورد UUID برابر ۲٫۷۳۷ ثانیه طول کشیدند. محیط همان آزمایش مجاز emulation پشتیبانی‌نشدهٔ SQL Server روی ARM64 است. پذیرش رسمی یادگیری و آمادگی production جدا می‌مانند.
