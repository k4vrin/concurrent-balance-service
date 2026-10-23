# بررسی plan اجرا و throughput در SQL Server

وضعیت: روش بررسی. هنوز هیچ query یا workload مالی روی SQL Server اندازه‌گیری نشده است. [English](../en/Query-performance.md)

## مسئله: query سریع لزوماً عملیات سریع نیست

مثلاً lookup حساب ممکن است plan خوبی داشته باشد، اما عملیات منتظر قفل همان حساب بماند. برای تشخیص گلوگاه باید هزینهٔ اجرای query را از lock wait، connection pool و صف برنامه جدا کنیم. این سند روش جمع‌آوری همین شواهد را توضیح می‌دهد.

## فهرست query‌ها

| شناسه و مرحله | query | مسیر دسترسی نامزد | مسئلهٔ هم‌زمانی برای بررسی جداگانه |
| --- | --- | --- | --- |
| Q01 / P1 | یافتن و قفل‌کردن حساب | کلید اصلی حساب | رقابت حساب پرترافیک و SQL واقعی Hibernate |
| Q02 / P1 | یافتن شناسهٔ تراکنش | index یکتای شناسه | مجوز عامل احرازشده، collation و فاصلهٔ انتهایی |
| Q03 / P4 | خواندن intent و مالک و وضعیت | کلید اصلی intent | ابتدا قفل intent، سپس حساب‌ها با ترتیب ثابت؛ ثبت تلاش ناموفق |
| Q04 / P6 | تصاحب موقت تعداد محدودی ردیف outbox | بررسی وضعیت، زمان تلاش و شناسه | مالکیت پردازشگر، انقضای lease و توقف ناگهانی |
| Q05 / P6 | صفحه‌بندی تاریخچه | بررسی حساب، زمان و شناسه | صفحه‌بندی keyset و معیار ثابت رفع تساوی |
| Q06 / P6 | درج هویت پیام مصرف‌شده | هویت یکتای consumer و رویداد | ثبت هویت حذف اثر تکرار و اثر پیام در یک تراکنش |

مسیرهای جدول فرضیه‌اند. SQL واقعی، نوع bind، implicit conversion، isolation و تنظیماتی مثل `READ_COMMITTED_SNAPSHOT` را قبل از انتخاب hint یا index بررسی کنید.

SQL Server می‌تواند key، page یا table را قفل کند و lock escalation داشته باشد. lock order حساب‌ها حذف تمام deadlock‌ها را ثابت نمی‌کند. deadlock victim به retry محدودِ کل تراکنش با همان هویت نیاز دارد.

## دریافت plan واقعی

### ۱. محیط را ثبت کنید

commit، schema، نسخه و edition سرور، compatibility level، تعداد ردیف و data skew، تازگی statistics، اندازهٔ connection pool و bind‌های مصنوعی را ثبت کنید.

### ۲. دادهٔ مقایسه‌پذیر بسازید

دادهٔ نماینده را خارج از بازهٔ زمان‌گیری seed کنید و statistics مرتبط را بگیرید. baseline و after باید داده و تنظیمات مشابه داشته باشند.

### ۳. actual plan بگیرید

در SSMS گزینهٔ Include Actual Execution Plan را فعال کنید. query واقعی را با پارامترها و fetch behavior برنامه اجرا کنید. estimated plan آمار اجرای واقعی ندارد.

### ۴. IO و زمان را ذخیره کنید

در session تشخیصی `SET STATISTICS IO ON` و `SET STATISTICS TIME ON` را فعال کنید. فایل `.sqlplan` و Messages را ذخیره کنید. اجرای parameterized باید مطابق برنامه باشد؛ literal SQL ممکن است plan متفاوتی بگیرد.

برای نمایش، cache مشترک production را پاک نکنید.

### ۵. plan را بخوانید

این موارد را مقایسه کنید:

- estimated rows و actual rows؛
- seek، scan، key lookup و logical reads؛
- spill، sort، implicit conversion و memory grant؛
- CPU، elapsed time و حساسیت به پارامتر.

scan لزوماً بد نیست. optimizer cost هم معادل throughput نیست.

### ۶. انتظار را از اجرا جدا کنید

execution cost، blocking، deadlock، pool wait و صف برنامه را جدا بررسی کنید. در صورت نیاز از دسترسی diagnostic مجاز و Extended Events برای شواهد deadlock استفاده کنید. lookup سریع هم می‌تواند منتظر hot account بماند.

### ۷. یک تغییر را آزمایش کنید

هر بار یک فرض را تغییر دهید، plan را دوباره بگیرید و بار مشابه و صحت را بررسی کنید. instrumentation جزئی نباید بدون حساب‌کردن سربارش در baseline load باقی بماند.

### نمونهٔ تشخیصی

این SQL فقط بعد از وجود schema متناظر قابل‌اجراست. نوع، طول و collation پارامتر را با ستون واقعی یکسان بگیرید؛ پارامتر رشته‌ای را روی ستون indexشده با نوع دیگر کپی نکنید:

```sql
SET STATISTICS IO ON;
SET STATISTICS TIME ON;

EXEC sys.sp_executesql
    N'SELECT transaction_id, request_hash, outcome
      FROM dbo.operations WHERE transaction_id = @tx_id;',
    N'@tx_id varchar(64)',
    @tx_id = 'synthetic-operation-001';

SET STATISTICS TIME OFF;
SET STATISTICS IO OFF;
```

فرمان‌ها statistics همان session را روشن و بعد خاموش می‌کنند؛ خروجی IO و زمان برای plan review است. فعلاً جدول `operations` وجود ندارد. query‌های lock و outbox بعد از پیاده‌سازی، plan و concurrency test جدا می‌خواهند.

تراکنش diagnostic که lock نگه می‌دارد باید صریحاً commit یا rollback شود. برای گرفتن plan، دسترسی گستردهٔ سرور به برنامه ندهید. plan ممکن است مقدار حساس داشته باشد؛ فقط شواهد مصنوعی و پاک‌سازی‌شده منتشر کنید.

## شواهد بار

### workload‌های جدا

حساب مستقل، hot account، duplicate، ترکیب read و mutation و قطعی dependency را جدا بسنجید. سخت‌افزار، نسخه، durability، حجم و skew، pool، offered load و request mix ثابت بمانند. warmup و تکرار و پراکندگی را گزارش کنید.

### معیارهای گزارش

- عملیات موفق commit‌شده در ثانیه و offered load؛
- rejection و error rate؛
- p50 و p95 و p99 latency؛
- logical reads، transaction log و انتظار lock و pool؛
- CPU و GC.

رد سریع‌تر درخواست، throughput مالی بیشتر نیست. فعلاً SLO عددی یا درصد overhead نداریم.

### محل نگهداری شواهد

هر run در `docs/performance/<query-id>/<run-id>/` ثبت می‌شود:

1. تاریخ و commit، schema، نسخه و compatibility؛
2. SQL و bind‌های پاک‌سازی‌شده، dataset، plan و statistics؛
3. baseline و تغییر، raw sample‌ها و پراکندگی latency و throughput؛
4. نتیجهٔ صحت، گلوگاه باقی‌مانده و تصمیم keep یا revert.

خروجی حجیم می‌تواند با روش بازتولید مستند ignored بماند. Query Store بعداً برای plan history مفید است؛ وجود و تنظیمش را روی نسخهٔ منتخب بررسی کنید.

منابع: [plan واقعی](https://learn.microsoft.com/en-us/sql/relational-databases/performance/display-an-actual-execution-plan)، [IO](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-io-transact-sql)، [TIME](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-time-transact-sql)، [locking](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide). با نسخهٔ پروژه تطبیق داده شوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

query‌های P1/P2 باید journal با operation ID یکتا، posting حساب با شناسهٔ پایدار سطر، جمع journal به‌ازای ارز و بازسازی projection شامل افتتاحیه را پوشش دهند. این query‌های دفترکل مرجع از تاریخچهٔ Kafka در P6 جدا هستند.

reconciliation از fixture بدون writer یا snapshot سازگار استفاده می‌کند. فضای posting و index، log و contention حساب تأمین در credit/debit باید سنجیده شوند. تست پیشرفت مستقل مجموعهٔ کامل حساب‌های بدون اشتراک می‌خواهد. هنوز هیچ‌کدام از این query‌ها یا اندازه‌گیری‌ها اجرا نشده‌اند.
