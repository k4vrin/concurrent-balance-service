> وضعیت فعلی مرور در ۲۰۲۶-۱۰-۲۳: مرور مهندسی CBS02–04 برای ورود به CBS09 کامل است. [قرارداد پیاده‌سازی](CBS09-contract.md) ذخیره‌سازی، SQL قفل، قیدها، مقدار اولیهٔ retry و اختیار را انتخاب می‌کند. بندهای انتخاب باز در ادامه تاریخی‌اند، مگر در قرارداد حفظ شده باشند. تأیید migration/entity/عملیات مالی در CBS09–20 انجام می‌شود؛ DDL کسب‌وکار اعمال نشده است.

# مرور ذخیره‌سازی دقیق شناسهٔ تراکنش

[English](../en/Transaction-id-storage.md) · [درخواست canonical](Canonical-request.md) · [مرور persistence](Persistence-review.md) · [راهنمای تست](SQL-server-tests.md)

وضعیت در ۲۰۲۶-۱۰-۲۳: گزینهٔ فنی مرورشده و تست‌شده برای CBS02–04. این شواهد SQL Server از جدول موقت به دست آمده‌اند. migration کسب‌وکار، repository زمان اجرا برای هویت، decoder دادهٔ canonical ذخیره‌شده و replay ماندگار نداریم. مرور باقی‌ماندهٔ تراکنش، schema و مجوزها همچنان پیش از CBS09 لازم است.

## قرارداد

دامنهٔ پذیرفته‌شدهٔ TransactionId از ۱ تا ۱۲۸ کاراکتر ASCII شامل حروف، رقم و `.`, `_`, `:`, `-` است. متن دقیق حفظ شود؛ trim، تغییر حروف یا Unicode normalization انجام ندهیم. UUID حساب، شناسهٔ داخلی ردیف عملیات و fingerprint درخواست canonical مفهوم‌های جدا هستند.

| ورودی | رفتار موردنیاز |
| --- | --- |
| `transfer-001` و `Transfer-001` | دو هویت معتبر و متفاوت |
| `transfer-001` تکراری دقیق | برخورد با قید یکتای سراسری با نام مشخص |
| `transfer-001 ` یا ` transfer-001` | ورودی نامعتبر؛ نباید alias حاصل از normalization شود |
| `Ａ` تمام‌عرض، حروف و رقم فارسی، emoji | ورودی نامعتبر؛ تبدیل به جایگزین ASCII ممنوع |
| خالی، null، NUL، فاصله یا ۱۲۹ کاراکتر | ورودی نامعتبر |

یکتایی transaction ID در جدول operations باید همهٔ نوع‌های مالی را پوشش دهد. نوع عملیات را به کلید یکتا اضافه نکنیم. این کلید، intent و نتیجهٔ ذخیره‌شده را پیدا می‌کند. سپس canonical bytes مشخص می‌کنند که استفادهٔ دوباره از شناسه همان معنای مالی را دارد یا نه. یکتایی به‌تنهایی replay منطبق را از استفادهٔ متعارض جدا نمی‌کند.

## ستون و قید پیشنهادی

ستون Unicode از نوع `nvarchar(256)` با `Latin1_General_100_BIN2` صریح، NOT NULL، قید یکتا، CHECK طول بایتی و CHECK کاراکتر به کار ببریم. در DDL آیندهٔ operations نام پایداری مانند `uq_operations_transaction_id` مناسب است؛ fixture برای جلوگیری از برخورد نام در tempdb پسوند تولید می‌کند.

fixture موجود در source با نام‌های تولیدشدهٔ خودش این ساختار را می‌سازد. این DDL نمونه است و فرمان اعمال schema کسب‌وکار نیست:

```sql
CREATE TABLE <review_fixture> (
    transaction_id nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT <unique_name> UNIQUE (transaction_id),
    CHECK (DATALENGTH(transaction_id) BETWEEN 2 AND 256),
    CHECK (
        CONVERT(varbinary(512), TRANSLATE(
            transaction_id COLLATE Latin1_General_100_BIN2,
            N'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-',
            REPLICATE(N'A', 66)
        )) = CONVERT(varbinary(512),
            REPLICATE(N'A', DATALENGTH(transaction_id) / 2))
    )
);
```

هر کاراکتر ASCII مجاز در nvarchar دو بایت می‌گیرد. بنابراین CHECK طول، از ۱ تا ۱۲۸ کاراکتر مجاز را می‌پذیرد. NOT NULL جداست، چون CHECK در SQL ممکن است نتیجهٔ unknown را بپذیرد. index یکتا شناسه‌های معتبر ذخیره‌شده را با حساسیت به حروف مقایسه می‌کند. ستون عمداً از دامنه بزرگ‌تر است؛ اگر مقدار طولانی هنگام binding بی‌صدا به ۲۵۶ واحد کد بریده شود، همچنان قید ۱۲۸ کاراکتری را نقض می‌کند. این انتخاب عرض اعلام‌شدهٔ index را بیشتر می‌کند؛ هزینهٔ throughput یا storage اندازه‌گیری نشده است.

ستون بزرگ‌تر نمی‌تواند تشخیص دهد که نویسنده عمداً پیش از درج، ورودی را به nvarchar(128) تبدیل کرده است. هیچ قیدی ورودی حذف‌شده را بازیابی نمی‌کند. نویسندهٔ runtime باید مقدار Unicode اصلی و معتبر را bind کند و تبدیل اتلافی نداشته باشد. fixture از `setNString` در JDBC استفاده می‌کند؛ پیش از lookup هم مانند insert از TransactionId اعتبارسنجی کنیم. برابری SQL برای متن نامعتبرِ دارای فاصلهٔ انتهایی همچنان padding دارد؛ این ستون مجوز query با شناسهٔ اعتبارسنجی‌نشده نیست.

## دلیل بررسی کاراکتر با بایت

TRANSLATE هر یک از ۶۶ کاراکتر مجاز را به `A` تبدیل می‌کند. شناسهٔ مجاز با طول n به n نسخه از `A` تبدیل می‌شود. CHECK نتیجه و رشتهٔ marker موردانتظار را به varbinary تبدیل و مقایسه می‌کند. کاراکتر نامجاز پس از ترجمه باقی می‌ماند و بایت متفاوت می‌سازد. DATALENGTH بایت را بدون پنهان‌کردن فاصلهٔ انتهایی یا NUL می‌شمارد. تبدیل به code page غیر Unicode یا مقایسهٔ هویت فقط بر اساس hash نداریم.

مثلاً `Ab:-9` به `AAAAA` تبدیل می‌شود و می‌گذرد. فاصلهٔ داخل `Ab 9` بایت متفاوت با `A` دارد و رد می‌شود. `A` سپس NUL و سپس `B` هم رد می‌شود. collation باینری صریح، بررسی را از قواعد برابری زبانی جدا می‌کند. [مستند TRANSLATE در Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/functions/translate-transact-sql?view=sql-server-ver17) نگاشت کاراکتر را شرح می‌دهد؛ تست‌های زیر همین expression را روی SQL Server 2022 مشخص‌شده می‌سنجند.

ستون یکتای BIN2 بدون CHECK ساختار، رشتهٔ نامجاز را هم می‌پذیرد و مشکل مقایسهٔ فاصلهٔ انتهایی SQL باقی می‌ماند. تبدیل به varchar پیش از اعتبارسنجی می‌تواند ورودی اصلی Unicode را از بین ببرد. فیلتر مبتنی بر LIKE هم برای کاراکتر کنترلی بررسی ویژه می‌خواهد. این گزینه مقدار Unicode حفظ‌شده را با مجموعهٔ دقیق کاراکترهای مجاز بررسی می‌کند.

## مثال درج رقیب

تست از دو اتصال جدا و شناسهٔ یکسان `competing-id` استفاده می‌کند:

1. اتصال A، autocommit را غیرفعال می‌کند و شناسه را بدون commit درج می‌کند.
2. اتصال B همان درج را با LOCK_TIMEOUT برابر ۲۵۰ امتحان می‌کند. تا بازبودن A خطای 1222 می‌گیرد. B کل تلاش را rollback می‌کند.
3. اگر A، commit کند، تلاش تازهٔ B خطای 2627 می‌گیرد که نام قید هویت تولیدشده در آن است.
4. اگر A، rollback کند، B می‌تواند شناسه را درج و commit کند.

این مشاهده از رزرو پیش از قفل حساب در پروتکل پیشنهادی پشتیبانی می‌کند، اما replay را پیاده نمی‌کند. پس از duplicate با نام مشخص، coordinator آینده باید از تراکنش شکست‌خورده خارج شود و آن را rollback کند، سپس canonical intent و نتیجهٔ committed را از مرز تازه بازیابی کند. همهٔ خطاهای 2627 یا integrity را idempotency تلقی نکنیم. کلید یکتای دیگر یا خطای CHECK/FK رسیدگی خودش را می‌خواهد. timeout قفل خطای فنی می‌ماند و نباید به رد ماندگار مشتری تبدیل شود.

## تست‌ها و محدودیت‌ها

[SqlServerTransactionIdIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTransactionIdIT.java) و [TransactionIdSqlFixture](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/TransactionIdSqlFixture.java) از اتصال محافظت‌شدهٔ cbs_test و جدول موقت سراسری با نام تولیدشده استفاده می‌کنند. مجوز اضافه نمی‌کنند و accounts/operations/journals/postings نمی‌سازند.

موارد تست شامل round trip دقیق، تفاوت حروف، هر ۶۶ کاراکتر مجاز در یک شناسه، مرز ۱ و ۱۲۸، خطای duplicate با نام قید، ۲۴ ورودی نامعتبر، NOT NULL، غیرفعال‌بودن هشدار truncation و دو نتیجهٔ درج رقیب هستند. query جداگانهٔ مجموعه‌ای همهٔ ۶۵٬۵۳۶ واحد کد منفرد UTF-16 را بین کاراکترهای ASCII بررسی می‌کند؛ دقیقاً ۶۶ مورد شرط کاراکتر را می‌گذرانند و اختلاف با بازه‌های موردانتظار ASCII صفر است. این query یک تست است، نه ۶۵٬۵۳۶ مورد JUnit. رد کاراکتر supplementary با درج واقعی emoji هم تست شده است. همهٔ رشته‌های چندکاراکتری Unicode را exhaust نمی‌کنیم.

این شواهد رفتار کاراکتر و یکتایی گزینهٔ پیشنهادی را از مسیر native JDBC تأیید می‌کنند. DDL نهایی operations باید همین قیدها، collation صریح و binding از نوع Unicode را حفظ کند؛ سپس تست‌ها روی migration واقعی دوباره اجرا شوند. replay از HTTP تا دیتابیس، شناسهٔ یکسان با canonical intent متفاوت، commit نامطمئن، مجوز migration/runtime، plan index و اتمیک‌بودن کامل مالی کارهای جدا هستند. محیط SQL Server در ARM64 همچنان آزمایش emulation پشتیبانی‌نشده است.

## گام مرور شناسهٔ تراکنش، ۲۰۲۶-۱۰-۲۳

گزینهٔ فنی تست‌شده nvarchar(256) با collation صریح BIN2، NOT NULL، قید طول و کاراکتر برای ۱ تا ۱۲۸ کاراکتر و یکتایی سراسری با نام مشخص است. binding از نوع Unicode در JDBC، ورودی نامعتبر را برای ردشدن حفظ می‌کند. سی‌ویک مورد واقعی تازه شامل بررسی همهٔ واحدهای کد منفرد BMP و درج رقیب هستند. اجرای کامل profile با Java 21 در ۱۲٫۷۵۶ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۴۰ تست SQL Server را بدون failure، error یا skip گذراند؛ کلاس تازه ۲٫۰۹۱ ثانیه طول کشید. آمار قبلی تاریخی است. migration کسب‌وکار، repository زمان اجرا یا replay ماندگار اضافه نشده است. گام بعد، مرور adapter قفل حساب UUID و قرارداد کامل تراکنش چهار جدول و مجوزهاست؛ پذیرش رسمی یادگیرنده باز می‌ماند.
