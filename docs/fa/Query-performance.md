# مرور پلن اجرا و throughput در SQL Server

وضعیت: فقط روش بررسی؛ query یا workload مالی روی SQL Server اندازه‌گیری نشده است. [English](../en/Query-performance.md)

## فهرست query

| شناسه / مرحله | query | مسیر دسترسی نامزد | مسئلهٔ هم‌زمانی جدا |
| --- | --- | --- | --- |
| Q01 / P1 | lookup و قفل حساب | PK حساب | رقابت hot account و SQL واقعی Hibernate |
| Q02 / P1 | lookup transaction ID | index یکتای شناسه | دسترسی actor، collation و فاصلهٔ انتهایی |
| Q03 / P4 | intent و مالک/وضعیت | PK intent | ابتدا intent سپس حساب مرتب؛ ثبت تلاش ناموفق |
| Q04 / P6 | claim محدود outbox | بررسی status/زمان تلاش/ID | مالکیت worker، انقضای lease و crash |
| Q05 / P6 | صفحهٔ تاریخچه | بررسی حساب/زمان/ID | keyset و tie-breaker ثابت |
| Q06 / P6 | درج dedup مصرف‌کننده | هویت یکتای consumer/event | ثبت dedup و اثر در یک تراکنش |

این‌ها فرض هستند. SQL واقعی، نوع bind، implicit conversion، isolation و تنظیماتی مانند READ_COMMITTED_SNAPSHOT بررسی شوند. SQL Server می‌تواند key/page/table را قفل و lock escalation انجام دهد. ترتیب حساب به‌تنهایی اثبات حذف همهٔ deadlockها نیست. قربانی deadlock به retry محدود کل تراکنش با هویت ثابت عملیات نیاز دارد.

## دریافت پلن واقعی

۱. commit، schema، نسخه/edition سرور، compatibility level، حجم/skew داده، تازگی statistics، pool و bind مصنوعی ثبت شوند.

۲. دادهٔ نماینده خارج از بازهٔ زمان‌گیری ساخته و statistics مرتبط جمع‌آوری شود. baseline و after داده و تنظیمات مشابه داشته باشند.

۳. در SSMS گزینهٔ Include Actual Execution Plan فعال و query واقعی با پارامتر و fetch برنامه اجرا شود. پلن تخمینی آمار اجرای واقعی نیست.

۴. در session تشخیصی `SET STATISTICS IO ON` و `SET STATISTICS TIME ON` روشن شوند. فایل `.sqlplan` و Messages ذخیره شود. اجرای parameterized مطابق برنامه باشد؛ literal ممکن است پلن دیگری بگیرد. cache مشترک production برای نمایش پاک نشود.

۵. estimated/actual rows، seek/scan، key lookup، logical reads، spill، sort، implicit conversion، memory grant و CPU/elapsed time و حساسیت پارامتر بررسی شوند. scan الزاماً بد و cost معادل throughput نیست.

۶. هزینهٔ اجرا از blocking، deadlock، انتظار pool و صف برنامه جدا شود. در صورت نیاز دسترسی مجاز diagnostic و Extended Events برای شواهد deadlock استفاده شود. lookup سریع هم می‌تواند منتظر حساب پرترافیک باشد.

۷. یک فرض تغییر و پلن و بار مشابه و صحت دوباره بررسی شوند. instrumentation جزئی نباید بدون محاسبهٔ هزینه داخل baseline بار باقی بماند.

نمونه فقط پس از وجود schema متناظر؛ نوع/طول/collation پارامتر با ستون یکسان باشد:

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

فعلاً جدول operations وجود ندارد. query قفل و outbox به تست concurrency و پلن جدا نیاز دارند. تراکنش diagnostic نگهدارندهٔ قفل صریحاً commit/rollback شود. برای پلن، دسترسی سروری گسترده به برنامه ندهیم. پلن ممکن است دادهٔ حساس داشته باشد؛ شواهد مصنوعی و پاک‌سازی‌شده منتشر شوند.

## شواهد بار

حساب مستقل، hot account، duplicate، ترکیب خواندن/تغییر و قطعی وابستگی جدا اندازه‌گیری شوند. سخت‌افزار، نسخه، durability، حجم/skew، pool، offered load و ترکیب درخواست ثابت باشند. warmup، تکرار و پراکندگی گزارش شوند. عملیات موفق commitشده در ثانیه، نرخ رد/خطا، p50/p95/p99، logical reads، transaction log، انتظار قفل/pool، CPU و GC ثبت شوند. رد سریع‌تر، throughput مالی بیشتر نیست. فعلاً SLO عددی یا درصد هزینه نداریم.

در `docs/performance/<query-id>/<run-id>/` تاریخ/commit، schema/نسخه/compatibility، SQL و bind پاک‌سازی‌شده، داده، پلن/statistics، تغییر، نمونهٔ خام، پراکندگی latency/throughput، کنترل صحت، گلوگاه و تصمیم حفظ/بازگشت ثبت شود. خروجی حجیم با روش بازتولید می‌تواند ignored باشد. Query Store در آینده برای تاریخچهٔ پلن مفید است؛ وجود و پیکربندی آن روی نسخهٔ منتخب بررسی شود.

منابع: [پلن واقعی](https://learn.microsoft.com/en-us/sql/relational-databases/performance/display-an-actual-execution-plan)، [IO](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-io-transact-sql)، [TIME](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-statistics-time-transact-sql)، [locking](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide). با نسخهٔ پروژه تطبیق داده شوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

queryهای P1/P2 برای سند با operation ID یکتا، posting حساب با شناسهٔ سطر پایدار، جمع سند به‌ازای ارز و بازسازی projection شامل افتتاحیه اضافه شوند. این queryهای دفترکل مرجع با تاریخچهٔ Kafka در P6 جدا هستند. تطبیق از fixture بدون writer یا snapshot سازگار استفاده کند. فضای posting/index، log و رقابت ردیف تأمین در credit/debit سنجیده شود. تست پیشرفت مستقل مجموعهٔ کامل حساب‌های مجزا لازم دارد. هیچ query یا اندازه‌گیری هنوز اجرا نشده است.
