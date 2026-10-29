# رقابت transaction ID بین instanceها در CBS17

[English](../en/Cross-instance-replay.md) · [هم‌زمانی IDهای متفاوت](Concurrency-execution.md) · [بازیابی retry](Retry-execution.md) · [وضعیت پیاده‌سازی](Implementation-status.md)

## یک identity که به دو سرور می‌رسد

دو instance برنامه تقریباً هم‌زمان یک debit با transaction ID یکسان می‌گیرند. هر دو ابتدا هیچ عملیات تکمیل‌شده‌ای پیدا نمی‌کنند. فقط یک رزرو می‌تواند برنده شود؛ تراکنش بازنده باید پیش از خواندن نتیجهٔ commit‌شدهٔ برنده تمام شود. نیت همسان همان پاسخ اصلی را می‌دهد. مبلغ، حساب یا نوع عملیات متفاوت باید بدون اثر مالی اضافه conflict بگیرد.

کلاس `SqlServerCrossInstanceIdentityIT` اکنون این مسیر را با دو context واقعی HTTP و دیتابیس اختصاصی موجود SQL Server بررسی می‌کند. handlerها، adapterها، قواعد مالی، schema و تنظیمات تولیدی تغییر نکرده‌اند.

## برنامه‌های مستقل و رزرو هماهنگ

`RunningLedgerApplication` دو context در Spring با پورت‌های loopback موقت و جدا راه می‌اندازد. setup، جدا بودن instanceهای credit/debit/transfer handler، transaction manager، entity-manager factory و datasource را بررسی می‌کند. هر برنامه یک Hikari pool مستقل با دو اتصال دارد. هر دو از دیتابیس تست و سیاست funding صریح یکسان استفاده می‌کنند. حساب fixture با identity محدود initializer ساخته و از credit handler واقعی با history متوازن تأمین می‌شود.

کلاس مخصوص تست `ReservationGatePostProcessor` پیش از ساخت handlerها، روی storeهای واقعی JPA برای credit/debit/transfer، spy نصب می‌کند. هر spy پیش از اجرای SQL واقعی رزرو در `ReservationRaceGate` منتظر می‌ماند. رسیدن به این نقطه یعنی lookup اولیهٔ canonical در آن برنامه هیچ نتیجه‌ای پیدا نکرده است. barrier چهارثانیه‌ای هر دو رزرو اولیه را آزاد می‌کند؛ retryهای بعدی وارد barrier نمی‌شوند. ارسال client از helper موجود با مهلت محدود HTTP استفاده می‌کند و نتیجهٔ همهٔ workerها جمع می‌شود.

دیتابیس برنده را تعیین می‌کند. adapter بازنده باید `DuplicateOperation` بدهد که شاهد واقعی SQL Server با کد 2601/2627 و نام `uq_operations_transaction_id` را در زنجیرهٔ علت داشته باشد. هر race دقیقاً دو ورود به رزرو و یک تعارض واقعی constraint نام‌دار می‌خواهد. هیچ خطای duplicate یا outcome تکمیل‌شده‌ای تزریق نمی‌شود. پس از پایان هر دو درخواست، gate غیرفعال می‌شود تا replayهای بین برنامه‌ها بدون هماهنگی اجرا شوند.

## شانزده حالت

| رقابت | حالت‌ها | نتیجهٔ مورد انتظار |
| --- | --- | --- |
| نیت موفق یکسان | credit، debit و transfer | هر دو HTTP 200 و بایت‌های پاسخ اصلی یکسان؛ یک عملیات نهایی، یک journal متوازن و دو posting. |
| رد کسب‌وکار یکسان | credit بالاتر از سقف؛ debit و transfer با کسری وجه | هر دو همان پاسخ اصلی HTTP 409؛ یک عملیات ردشده و صفر journal/posting. replay پس از شارژ هم یکسان می‌ماند. |
| ID یکسان و مبلغ متفاوت | credit، debit و transfer | یک موفقیت و یک TRANSACTION_ID_CONFLICT؛ فقط مبلغ برنده روی موجودی/history اثر دارد. |
| ID یکسان و مشتری متفاوت | مقصد credit؛ مبدأ debit یا transfer | یک موفقیت و یک conflict؛ موجودی حساب بازنده تغییر نمی‌کند. |
| ID یکسان و نوع عملیات متفاوت | credit/debit، credit/transfer و debit/transfer | فضای identity سراسری فقط یک نوع عملیات و یک اثر مالی نگه می‌دارد. |
| ID یکسان و مقصد انتقال متفاوت | یک مبدأ و دو مقصد | فقط مقصد برنده وجه می‌گیرد؛ مقصد دیگر بدون تغییر می‌ماند. |

تست‌ها برنده‌شدن هرکدام از درخواست‌های اولیه را می‌پذیرند. SQL مستقل دقیقاً یک عملیات ماندگار با type، amount، state نهایی، status و بایت‌های پاسخ مورد انتظار را الزام می‌کند. موفقیت یک journal، دو posting و برابری دقیق DEBIT/CREDIT همان عملیات با `decimal(38,0)` می‌خواهد. رد، history مالی ندارد. PENDING ماندگار پذیرفته نمی‌شود.

موجودی مورد انتظار مشتری، مقصد و funding مستقل بررسی و سپس از postingهای immutable در یک query و بعد از پایان نویسنده‌ها بازسازی می‌شود. هر دو نیت رقیب از هر دو برنامه replay می‌شوند: درخواست همسان پاسخ اصلی و درخواست بازنده conflict را حفظ می‌کند. شمارش history و همهٔ projectionهای مرتبط بعد از replay دوباره بررسی می‌شوند. debit/transfer ردشده بعد از شارژ هم بدون بررسی دوبارهٔ وجه همان نتیجه را می‌دهند؛ رد credit بالاتر از سقف هم بعد از شارژ ماندگار است.

## بررسی، ۲۰۲۶-۱۰-۲۹

اجرای کامل Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۴۲۱ تست قابل‌حمل و ۱۷۸ تست واقعی SQL Server** را با **صفر failure، error و skip** در **55.043 ثانیه** گذراند. مجموع XMLهای تازهٔ Surefire/Failsafe بررسی شد.

اجرای متمرکز اولیه، هر ۱۶ تست تازهٔ integration را در کنار ۴۲۱ تست قابل‌حمل، در 20.442 ثانیه گذراند. هر race تعارض واقعی constraint نام‌دار SQL Server را مشاهده کرد. هر پنج type تازهٔ Java از Javadoc/doclint گذشتند. اسکن syntax tree با compiler، همهٔ ۱۵۲ فایل Java را بررسی کرد و هیچ type تودرتو، local یا anonymous پیدا نکرد. لینک‌های اسناد، whitespace و اسکن مقادیر دقیق credential در logها/reportها هم موفق بودند.

## محدودهٔ شواهد و معیارهای بعدی

منابع Spring و persistence هر برنامه مستقل ساخته می‌شوند، اما هر دو در یک JVM و host هستند. gate مشترک فقط ابزار هماهنگی تست است و سازوکار هماهنگی تولیدی نیست. این حالت‌ها raceهای منتخب HTTP/دیتابیس بین contextها را ثابت می‌کنند؛ جداسازی crash بین processها، همهٔ interleavingها، قطع واقعی شبکه یا throughput را ثابت نمی‌کنند.

انتقال‌های معکوس، پیشرفت حساب مستقل هنگام قفل‌بودن حساب دیگر و بازیابی گسترده‌تر خطاهای پاسخ/commit در CBS18–19 می‌مانند. احراز هویت/مالکیت، پذیرش مستقل یادگیری و آمادگی production جدا هستند. شبیه‌سازی ARM64 موجود SQL Server خارج از پشتیبانی Microsoft است.

CBS16 با fast-forward وارد main و با `773c451` push شد. این checkpoint شامل suite رقابت بین instanceها روی `test/cross-instance-idempotency-races` و بررسی کامل Javadoc سطح type است.
