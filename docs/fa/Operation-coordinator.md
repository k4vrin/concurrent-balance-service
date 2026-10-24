# طراحی coordinator عملیات و قفل‌گذاری SQL Server

[English](../en/Operation-coordinator.md) · [Persistence](Persistence.md) · [قرارداد پذیرفته‌شده](CBS09-contract.md) · [ADR-010](../adr/ADR-010-persistence-contract.fa.md)

این راهنما پایهٔ coordinator اضافه‌شده در ۲۰۲۶-۱۰-۲۴، دلیل انتخاب طراحی و پروتکل قفل حساب را توضیح می‌دهد. کد فعلی، رفتار مالی برنامه‌ریزی‌شده و شواهد تست جدا هستند. `READ_COMMITTED` سطح isolation دیتابیس است؛ rate limiting تعداد درخواست‌های ورودی را محدود می‌کند و موضوع دیگری است.

## مسئله‌ای که باید حل کنیم

یک credit چند رکورد مرتبط می‌سازد یا تغییر می‌دهد: هویت عملیات، journal متوازن، دو posting، projection موجودی هر دو حساب و پاسخ موفقیت ذخیره‌شده. همه باید با هم commit شوند. موجودی بدون history قابل تطبیق نیست و history بدون projection یا نتیجهٔ متناظر هم ناقص است.

مسیر برنامه‌ریزی‌شده چنین است:

```text
Validate request shape outside the transaction
Coordinator starts its own transaction
    Reserve operation identity before account locks
    Resolve and lock the complete participant set in the shared order
    Validate locked state, policy and all proposed balances
    Persist journal, postings, projections and terminal response
Commit
Return the retained result
```

coordinator مرز تراکنش را کنترل می‌کند؛ handler آیندهٔ credit تصمیم مالی را می‌گیرد و adapterهای persistence با دیتابیس کار می‌کنند. ارسال پیام، نوشتن cache یا درخواست شبکه در این تلاش قفل‌شده جایی ندارد.

اگر پس از تغییر projection و پیش از ذخیرهٔ نتیجه خطای فنی رخ دهد و rollback تأیید شود، تلاش هیچ operation، journal یا posting تازه‌ای باقی نمی‌گذارد و موجودی هر دو حساب به مقدار قبلی برمی‌گردد. حساب‌های قبلاً commit‌شده حفظ می‌شوند. شکست گذرا با rollback قطعی ممکن است retry محدود کل تلاش با همان transaction ID را مجاز کند. نقص پایدار ابتدا به اصلاح نیاز دارد. خطای اتصال هنگام commit به بازیابی با همان شناسه نیاز دارد؛ صرف خطا شاهد rollback نیست.

## خواندن کد فعلی

منبع: [OperationCoordinator.java](../../src/main/java/dev/kavrin/balance/operation/application/OperationCoordinator.java).

```java
@Service
@Profile("!smoke")
public final class OperationCoordinator {
    private final TransactionTemplate transaction;

    public OperationCoordinator(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> attempt) {
        // ADR-010: joining a caller's transaction could return success before that caller commits.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Operation coordinator must own its transaction");
        }
        return transaction.execute(status -> attempt.get());
    }
}
```

`Supplier<T>` یک بلوک کار با خروجی از نوع `T` است. coordinator برای اجرای آن لازم نیست جزئیات credit، debit یا transfer را بداند. handler آینده می‌تواند رد تجاری ذخیره‌شده را به‌عنوان مقدار عادی برگرداند تا رکورد آن commit شود. exception غیرمنتظرهٔ runtime از callback خارج می‌شود و rollback را فعال می‌کند.

نتیجهٔ callback تا پایان تراکنش فقط یک نتیجهٔ پیشنهادی است:

```text
begin → callback returns candidate → commit succeeds → execute returns candidate
begin → callback throws           → rollback        → exception escapes
begin → callback returns candidate → commit throws  → exception escapes
```

حالت آخر به‌معنای rollback قطعی دیتابیس نیست. بازیابی commit نامطمئن بیرون از این کلاس حداقلی انجام خواهد شد. [راهنمای تراکنش برنامه‌ای Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html) سازوکار callback را شرح می‌دهد.

## چرا manager وابستگی واقعی runtime است؟

`TransactionTemplate` اجرای callback و تنظیمات تراکنش را فراهم می‌کند. `PlatformTransactionManager` مدیریت تراکنش را روی زیرساخت persistence انجام می‌دهد. در مسیر JPA این پروژه، manager موردنظر همان `JpaTransactionManager` کدهای persistence شرکت‌کننده است. اتصال این مسیر هنوز بخشی از پیاده‌سازی و تست مالی آینده است.

هر دو شیوهٔ ساخت زیر به manager نیاز دارند:

```java
var direct = new TransactionTemplate(manager);

var configuredLater = new TransactionTemplate();
configuredLater.setTransactionManager(manager);
```

سازندهٔ بدون آرگومان امکان تنظیم بعدی می‌دهد؛ manager دیتابیس نمی‌سازد. تزریق template ازپیش‌تنظیم‌شده هم انتخاب معتبری است. اینجا manager را دریافت می‌کنیم و template خصوصی می‌سازیم تا تنظیمات به coordinator تعلق داشته باشند و یک bean مشترک قابل‌تغییر را دست‌کاری نکنیم. constructor injection وابستگی را روشن می‌کند و تست با manager ثبت‌کننده را هم ممکن می‌سازد. قابلیت تست مزیت آن است؛ نیاز runtime به manager مستقل از تست است. [API مربوط به TransactionTemplate](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionTemplate.html) این الزام را توضیح می‌دهد.

## چرا تراکنش فعال فراخواننده را رد می‌کنیم؟

propagation پیش‌فرض template برابر REQUIRED است. بدون guard ممکن است به تراکنش caller بپیوندد:

```text
caller begins transaction
    coordinator joins it
    callback completes
    coordinator returns a candidate success
caller later rolls back
```

در این حالت coordinator پیش از مشخص‌شدن نتیجهٔ نهایی، موفقیت برگردانده است. guard مالکیت تراکنش را صریح می‌کند و تراکنش فعال قابل‌نوشتن و فقط‌خواندنی را هر دو رد می‌کند.

REQUIRES_NEW تراکنش مستقلی می‌سازد و تراکنش بیرونی را موقتاً معلق می‌کند. در نتیجه credit داخلی ممکن است commit شود، حتی اگر کار بیرونی بعداً rollback شود. این قرارداد دیگری برای ترکیب عملیات است؛ آن را پنهانی وارد طراحی نکرده‌ایم. [مرجع propagation در Spring](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html) این تفاوت را شرح می‌دهد.

استفاده از `@Transactional` هم معتبر است. template را انتخاب کردیم تا پایان یک تلاش و محل آیندهٔ تشخیص خطا، بازیابی شناسه و backoff پس از cleanup تراکنش در کد روشن باشد. حلقهٔ retry هنوز پیاده نشده است. این انتخاب به جریان کنترل این پروژه مربوط است و ضعف عمومی annotationها نیست.

## چرا READ_COMMITTED به‌تنهایی کافی نیست؟

در تنظیم محلی انتخاب‌شده، READ_COMMITTED_SNAPSHOT غیرفعال است. خواندن عادی READ_COMMITTED با قفل از dirty read جلوگیری می‌کند، ولی محافظت همهٔ مقادیر خوانده‌شده را تا پایان تراکنش نگه نمی‌دارد. اگر RCSI فعال باشد، خواندن عادی از نسخهٔ ردیف در سطح statement استفاده می‌کند؛ رفتار blocking مشاهده‌شدهٔ این پروژه را به آن تنظیم تعمیم ندهید.

جلوگیری از dirty read این توالی را ایمن نمی‌کند:

```text
A reads committed balance 100
B reads committed balance 100
A decides debit 80 is affordable
B decides debit 80 is affordable
```

هر دو مقدار committed خوانده‌اند، اما تصمیم مالی باید تا تغییر و commit محافظت شود. پروتکل ما پیش از خواندن state مؤثر بر تصمیم، قفل مناسب را می‌گیرد. [راهنمای قفل و row versioning مایکروسافت](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide?view=sql-server-ver16) مبانی این رفتار را توضیح می‌دهد.

## تفاوت UPDLOCK و HOLDLOCK

adapter داخل تراکنش قابل‌نوشتنِ مالک تلاش، این query پارامتری را اجرا می‌کند:

```sql
SELECT account_id, category, currency, balance, status
FROM dbo.accounts WITH (UPDLOCK, HOLDLOCK)
WHERE account_id = CAST(? AS uniqueidentifier);
```

`UPDLOCK` هنگام خواندن قفل update درخواست می‌کند و آن را تا پایان تراکنش نگه می‌دارد. این قفل قصد تغییر را مشخص می‌کند، ولی خودش UPDATE انجام نمی‌دهد. `HOLDLOCK` برای دسترسی اشاره‌شده به جدول، semantics سطح SERIALIZABLE را اعمال می‌کند؛ از جمله محافظت predicate در جایی که لازم است. کاربرد آن فقط طولانی‌کردن قفل ردیف نیست؛ lookup حساب غایب را هم در برابر درج مقدار مطابق محافظت می‌کند. [مرجع table hintهای مایکروسافت](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table) هر دو را تعریف می‌کند.

جدول ساده‌شدهٔ سازگاری برای یک منبع قفل مشترک:

| قفل موجود / درخواست تازه | Shared S | Update U | Exclusive X |
| --- | --- | --- | --- |
| Shared S | سازگار | سازگار | انتظار |
| Update U | سازگار | انتظار | انتظار |
| Exclusive X | انتظار | انتظار | انتظار |

این جدول modeهای intent و range و granularity را نشان نمی‌دهد. قفل U همان X نیست؛ یک reader با قفل سازگار می‌تواند پیش از تبدیل U به X بخواند. writer هنگام نیاز به X ممکن است منتظر reader بماند. plan، کلید index، page و escalation منابع واقعی قفل را تعیین می‌کنند. این hintها تضمین row-only یا پیشرفت مستقل همگانی نمی‌دهند.

### مثال UPDLOCK: کدام updater زودتر تصمیم بگیرد؟

با SELECT عادی در SERIALIZABLE، دو تراکنش می‌توانند روی حساب موجود قفل S بگیرند. اگر هر دو سپس UPDATE بخواهند، هر کدام ممکن است منتظر آزادشدن S دیگری بماند و SQL Server یکی را قربانی deadlock کند.

در lookup ما، updater دوم هنگام خواندن منتظر می‌ماند:

```text
A obtains U on customer C and reads 100
B requests U on C and waits
A validates debit 80, writes 20, commits
B obtains U and reads 20
B can now reject debit 80 using current protected state
```

این مثال مسیر آیندهٔ debit است، نه handler پیاده‌شده. این روش الگوی مشخص تبدیل S به X را کاهش می‌دهد، ولی همهٔ deadlockها را حذف نمی‌کند. ترتیب مشترک قفل چند حساب و بازیابی کل تلاش همچنان لازم‌اند.

### مثال HOLDLOCK: محافظت از نبودن حساب

فرض کنید حساب M وجود ندارد و lookup هیچ ردیفی برنمی‌گرداند. ردیف موجودی نداریم که update lock روی آن، تضمین کامل غیبت را بیان کند.

```text
A looks up missing M with UPDLOCK, HOLDLOCK
A retains protection for the searched key range
B tries to insert M and waits
A commits its missing-account rejection, or rolls back
B's insertion can proceed after protection is released
```

gap محافظت‌شده ممکن است کلیدهای دیگری هم داشته باشد؛ درج UUID غایب دیگری هم می‌تواند منتظر بماند. پوشش دقیق به index و plan بستگی دارد. lookup موفق کلید یکتا الزاماً همان range lock مربوط به کلید غایب را ندارد. بستن result set این قفل‌های تراکنشی را آزاد نمی‌کند. در autocommit، تراکنش statement با خود statement تمام می‌شود؛ پروتکل ما تراکنش صریحی می‌خواهد که تصمیم و نوشتن‌ها را در بر بگیرد.

## چرا کل تراکنش SERIALIZABLE نیست؟

انتخاب پذیرفته‌شده، READ_COMMITTED همراه با محافظت قوی‌تر روی lookupهای مالی حساب است. SERIALIZABLE این محافظت را به خواندن‌های عادی دیگر هم گسترش می‌دهد؛ همهٔ تراکنش‌های دیتابیس را به صف تک‌تایی تبدیل نمی‌کند. اگر workload فقط همین lookupهای محافظت‌شده و نوشتن‌ها باشد، تغییر isolation پایه شاید هزینه یا محافظت چندانی اضافه نکند. شاهد اندازه‌گیری‌شده‌ای نداریم که READ_COMMITTED مسیر فعلی credit را سریع‌تر کرده باشد.

### خواندن نقطه‌ای اضافی و انتظار طولانی‌تر

فرض آموزشی: A به مشتری A اعتبار می‌دهد، ولی با SELECT عادی و indexed، حساب B را هم برای اطلاعات می‌خواند و آن را تغییر نمی‌دهد. تراکنش دیگری می‌خواهد B را UPDATE کند.

| isolation تراکنش A | اثر خواندن عادی B |
| --- | --- |
| READ_COMMITTED مبتنی بر قفل | محافظت خواندن با پیشرفت و پایان خواندن آزاد می‌شود؛ خواندن تمام‌شده updater را تا commit تراکنش A متوقف نمی‌کند. |
| SERIALIZABLE | محافظت تا پایان A می‌ماند و UPDATE ناسازگار منتظر می‌ماند. |

اگر A در میلی‌ثانیهٔ ۲ مقدار B را بخواند و در میلی‌ثانیهٔ ۵۰ commit کند، updater ممکن است بخش زیادی از این فاصله را منتظر بماند. زمان‌ها صرفاً مثال‌اند. انتظار بیشتر latency را بالا می‌برد، connection را اشغال می‌کند و زیر contention می‌تواند throughput را کم کند؛ لزوماً CPU هر query بیشتر نمی‌شود. طراحی فعلی credit چنین خواندن اضافه‌ای از B نمی‌خواهد.

### خواندن range و توقف درج

یک query فرضی دیگر:

```sql
SELECT transaction_id
FROM dbo.operations
WHERE transaction_id >= N'credit-100'
  AND transaction_id <  N'credit-200';
```

در SERIALIZABLE، plan مبتنی بر range index می‌تواند درج `credit-150` را تا پایان تراکنش متوقف کند تا مجموعهٔ نتیجه ثابت بماند. query عادی READ_COMMITTED پس از پایان چنین rangeای را حفظ نمی‌کند. اگر تصمیم تجاری به ثابت‌ماندن مجموعه وابسته باشد این محافظت مفید است؛ برای خواندن صرفاً اطلاعاتی ممکن است انتظار اضافه بسازد. مسیر دسترسی نامناسب می‌تواند قفل‌ها را گسترده‌تر کند؛ index و plan مهم‌اند.

### حساب funding مشترک همچنان محل contention است

دو credit برای مشتری متفاوت ممکن است یک حساب funding را تغییر دهند. در هر دو طراحی باید روی آن حساب هماهنگ شوند. HOLDLOCK محلی هم range protection دارد؛ این روش blocking را حذف نمی‌کند. پیش از ادعای کارایی یا تغییر isolation باید plan، lock wait، deadlock، latency و throughput را روی workload یکسان مقایسه کنیم.

## نقش هر جزء و محدودیت کلاس فعلی

| سازوکار | مسئولیت |
| --- | --- |
| تراکنش متعلق به coordinator | commit یا rollback کل تلاش و بازگشت پس از پایان موفق |
| READ_COMMITTED | isolation پایهٔ خواندن‌های عادی |
| UPDLOCK / HOLDLOCK | محافظت state حساب مؤثر بر تصمیم و غیبت مرتبط |
| مجموعهٔ کامل و بدون تکرار حساب‌ها با ترتیب نزولی UUID در برنامه | کاهش deadlock ناشی از ترتیب مخالف؛ ترتیب GUID در SQL جایگزین آن نیست |
| یکتایی عمومی transaction ID و مقایسهٔ canonical | پشتیبانی از replay و conflict ماندگار |
| history متوازن تغییرناپذیر و projection و نتیجهٔ اتمیک | حفظ قرارداد پذیرفته‌شدهٔ ledger |
| recovery و reconciliation | بازیابی retry یا commit نامطمئن و بررسی مستقل اثر committed |

فعلاً فقط wrapper حداقلی تراکنش پیاده شده است. کلاس با `@Service` بیرون از profile بدون persistence یعنی `smoke` ثبت می‌شود؛ اتصال handler مربوط به credit هنوز باقی است. رزرو، snapshot سیاست، نتیجهٔ ذخیره‌شده، بودجهٔ retry و بازیابی commit نامطمئن باقی‌اند. کلاس اکنون isolation را تنظیم می‌کند، ولی timeout و تنظیمات آیندهٔ retry را ندارد. adapter حساب مسیر قفل مرتب‌شده را دارد؛ این مجموعه هنوز عملیات مالی عمومی کامل نیست.

## شواهد و ترتیب مرور

پنج مورد [OperationCoordinatorTests](../../src/test/java/dev/kavrin/balance/operation/application/OperationCoordinatorTests.java) با manager ثبت‌کننده، اجرای قابل‌نوشتن READ_COMMITTED، بازگشت پس از commit، rollback خطای callback، انتقال خطای commit و رد دو نوع تراکنش فعال را بررسی می‌کنند. اجرای Java 21 با `./mvnw -o -B -ntp verify` برای این پایه، ۲۴۵ تست مستقل از زیرساخت را بدون failure، error یا skip گذراند. این تست‌ها شاهد commit دیتابیس، replay ذخیره‌شده یا rollback در SQL Server نیستند.

[تست‌های persistence قبلی](Persistence.md)، طول عمر قفل واقعی JPA، تازگی state، خواندن committed در HTTP و مرزهای rollback افتتاحیه را بررسی کردند. اجرای کامل ثبت‌شده ۱۰۳ تست SQL Server و ۲۴۰ تست مستقل از زیرساخت داشت. این اجرای تاریخی پیش از coordinator بود و برای پایهٔ آن تکرار نشد. محیط SQL Server روی ARM64 همچنان آزمایش محلی بدون پشتیبانی رسمی است. timelineهای این راهنما مثال آموزشی‌اند، نه benchmark اجراشده.

ابتدا سازنده، سپس guard تراکنش فعال و بعد مرز نتیجهٔ پیشنهادی و commit را مرور کنید. تفاوت دو hint و مثال کلید غایب را با AccountStore مقایسه کنید. slice ماندگار credit باید این coordinator را با manager واقعی JPA و همهٔ نوشتن‌های مالی در یک تراکنش فیزیکی بیازماید.

## مرحلهٔ اتصال Spring، ۲۰۲۶-۱۰-۲۴

کلاس `OperationCoordinator` اکنون مانند اجزای persistence با `@Service` و `@Profile("!smoke")` ثبت می‌شود. در context عادی، Spring مدیر تراکنش را به سازنده تزریق می‌کند؛ context فقط‌آزمایشی smoke چون زیرساخت persistence ندارد، coordinator را کنار می‌گذارد. اتصال handler مربوط به credit هنوز باقی است. دو تست context تازه تزریق manager و کنارگذاشتن در smoke را بررسی می‌کنند. اجرای کامل Java 21 با `./mvnw -o -B -ntp verify` در ۸٫۲۱۹ ثانیه هر ۲۴۷ تست را بدون failure، error یا skip گذراند.

سه مورد تازه در `SqlServerPersistenceIT` برای مشارکت JPA و READ_COMMITTED و مشاهدهٔ probe پس از commit، مشاهدهٔ rollback از اتصال تازهٔ JDBC و رد تراکنش فعال JPA نوشته شده‌اند. از fixture موجود با جدول موقت و نام یکتا استفاده می‌کنند و history مالی نمی‌نویسند. این موارد هنوز تأیید نشده‌اند: تلاش نخست هنگام setup با connection refused شکست خورد؛ راه‌اندازی مجدد container موجود هم با خطای تخصیص page و خطاهای 17300/17312 در SQL Server ناموفق بود. اجرای کامل profile نیز هنگام setup دیتابیس ۱۷ error داشت؛ این نتیجه به‌معنای قبولی تست دیتابیس نیست. container با کد 235 خارج شد؛ volume حذف نشد و schema یا تنظیم منابع Docker تغییر نکرد. شاهد تاریخی ۱۰۳ تست، این گام را تأیید نمی‌کند. پس از بازیابی محیط محلی SQL Server باید `./mvnw -Psqlserver-it verify` دوباره اجرا شود تا بتوان اتصال واقعی JPA را پذیرفته دانست.

## اجرای موفق دوبارهٔ دیتابیس، ۲۰۲۶-۱۰-۲۴

تست‌های coordinator/JPA بالا اکنون تأیید شده‌اند. SQL Server داخل container سالم بود، ولی Docker با وجود تنظیم حفظ‌شدهٔ 127.0.0.1:14333، هیچ port فعال منتشر نکرده بود. restart همان container نگاشت را برگرداند؛ image، volume، schema یا تنظیم منابع تغییر نکرد. این نقص اتصال با شکست قبلی تخصیص حافظه متفاوت است و رفع آن علت شکست حافظه را مشخص نمی‌کند.

اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify` در ۲۷٫۷۰۶ ثانیه، ۲۴۷ تست مستقل از زیرساخت و ۱۰۶ تست SQL Server را بدون failure، error یا skip گذراند. مجموع گزارش‌های XML تازه بررسی شد. هر سه مورد تازه موفق بودند: manager تزریق‌شده از نوع JpaTransactionManager است، EntityManager به تراکنش READ_COMMITTED می‌پیوندد، اتصال دیگر probe committed را می‌بیند، probe ناموفق rollback می‌شود و تراکنش فعال JPA پیش از اجرای کار رد می‌شود. probe موقت جدول تولیدشدهٔ خودش را پاک می‌کند؛ تست عادی افتتاحیه history مصنوعی committed را نگه می‌دارد. هیچ رمز تنظیم‌شده‌ای در log نهایی یا گزارش‌ها نبود. وضعیت تأییدنشدن بالا با این نتیجه جایگزین می‌شود، ولی سابقهٔ شکست حفظ می‌ماند. اجرای credit، replay/recovery و پذیرش رسمی یادگیرنده هنوز باقی‌اند؛ اجرای ARM64 همچنان پشتیبانی رسمی ندارد.
