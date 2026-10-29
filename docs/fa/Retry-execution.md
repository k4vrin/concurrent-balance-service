# retry تراکنش و بازیابی نتیجهٔ نامعلوم در CBS15

[English](../en/Retry-execution.md) · [قرارداد پذیرفته‌شده](CBS09-contract.md) · [coordinator](Operation-coordinator.md) · [وضعیت پیاده‌سازی](Implementation-status.md)

## مسئله‌ای که باید حل کنیم

فرض کنید از حسابی با موجودی ۱۰۰ ریال، ۴۰ ریال انتقال می‌دهیم. عملیات ID خود را رزرو می‌کند، اما حساب قفل است و SQL Server خطای 1222 می‌دهد. این خطا ممکن است تراکنش را باز نگه دارد. retry همان statement کافی نیست؛ ابتدا کل تراکنش باید rollback شود، سپس نتیجهٔ همان ID بررسی شود و فقط بعد از آن اجرای کامل دوباره مجاز است.

گم‌شدن تأیید commit معنای دیگری دارد. ممکن است انتقال در دیتابیس commit شده باشد. در این حالت نباید نتیجهٔ موقت callback را موفقیت قطعی بدانیم یا انتقال را بی‌درنگ تکرار کنیم. باید پاسخ ماندگار را بازیابی کنیم؛ اگر نتیجه روشن نشد، HTTP 503 می‌دهیم و caller همان transaction ID را نگه می‌دارد.

## مسیر فعلی اجرا

credit، debit و transfer از `OperationCoordinator.executeRecovering` استفاده می‌کنند. متد `execute` همچنان فقط یک تراکنش اجرا می‌کند و برای استعلام موجودی و هر خواندن بازیابی به کار می‌رود. coordinator پیش از lookup یا آماده‌سازی، وجود تراکنش بیرونی را رد می‌کند.

1. ابتدا نتیجهٔ commit‌شده با نیت کامل canonical بررسی و پاسخ ماندگار اعتبارسنجی می‌شود. سیاست فعلی ارز، سقف و funding بعد از این مرحله بررسی می‌شود.
2. operation ID، journal ID، command، snapshot سیاست و انتخاب funding فقط یک بار آماده می‌شوند. همهٔ retryهای نوشتن همان شیء attempt را دریافت می‌کنند.
3. کل callback شامل رزرو، قفل، بررسی، history، projection و outcome در یک تراکنش مستقل اجرا می‌شود. `AttemptCompletion` وضعیت `afterCompletion` در Spring را ثبت می‌کند.
4. بعد از rollback گزارش‌شده، خطا بیرون از تراکنش طبقه‌بندی می‌شود. کد موقت مجاز، ابتدا lookup تازهٔ همان ID و سپس retry محدود کل attempt را ممکن می‌کند. رد کسب‌وکار یک مقدار عادی commit‌شده است و retry نمی‌شود.
5. اگر پایان تراکنش نامعلوم باشد یا تأیید commit شکست بخورد، فقط خواندن‌های محدود برای بازیابی اجرا می‌شوند. نتیجهٔ همسان پاسخ اصلی را می‌دهد؛ نیت متفاوت conflict است. lookup خالی، حتی پس از چند بار خواندن، مجوز نوشتن دوباره در همین درخواست نیست. نتیجهٔ حل‌نشده با 503 و علت خطای اصلی برمی‌گردد.

هر attempt تازه، تراکنش و persistence context تازه دارد. تصمیم بازیابی و jitter بعد از cleanup تراکنش قبلی اجرا می‌شوند. deadline هرگز cleanup را قطع نمی‌کند و نتیجهٔ نامعلوم را rollback قطعی تلقی نمی‌کند.

## تصمیم برای هر خطا

| شاهد | رفتار |
| --- | --- |
| rollback گزارش‌شده و کد SQL Server برابر 1205 یا 1222 | lookup تازهٔ همان ID؛ اگر نتیجه نبود و budget کافی بود، اجرای کامل attempt آماده‌شده تکرار می‌شود. |
| تعارض constraint نام‌دار transaction ID که به `DuplicateOperation` تبدیل شده | بازیابی بعد از پایان تراکنش شکست‌خورده؛ بازگشت نتیجهٔ ماندگار یا conflict. lookup خالی، unavailable است. |
| خطای commit یا cleanup/پایان نامعلوم | فقط بازیابی محدود؛ هیچ retry نوشتنی مجاز نیست، حتی پس از lookupهای خالی. |
| unique key دیگر، خطای CHECK/FK، overflow یا دادهٔ ماندگار خراب | خطای فنی عبور می‌کند؛ retry یا تبدیل به کسری وجه/replay نداریم. |
| خطای کلی lock در Spring بدون کد vendor، لغو query در driver یا خطای اتصال پس از rollback قطعی | unavailable محافظه‌کارانه؛ این خطاها خودکار معادل 1205/1222 نیستند. |
| interrupt یا budget ناکافی | پذیرش کار تازه متوقف می‌شود؛ interrupt حفظ می‌شود و unavailable برمی‌گردد. |

`SqlServerFailureClassifier` زنجیرهٔ cause و next-exception در JDBC را با محافظت در برابر cycle می‌خواند. SQLState از دستهٔ 08، کد vendor غیرصفر دیگری یا خطای suppressed مربوط به cleanup، مجوز retry را لغو می‌کند. تشخیص فقط `uq_operations_transaction_id` همچنان مسئولیت adapterهای store است؛ unique failure دیگر duplicate محسوب نمی‌شود.

## تنظیمات و budget

| تنظیم | پیش‌فرض | اعتبارسنجی یا مسئولیت |
| --- | --- | --- |
| `balance.retry.max-attempts` / `BALANCE_RETRY_MAX_ATTEMPTS` | 3 | بین ۱ و ۱۰ attempt نوشتن؛ هر مرحلهٔ بازیابی نیز با همین تعداد محدود می‌شود. |
| `balance.retry.budget-millis` / `BALANCE_RETRY_BUDGET_MILLIS` | 20000 | بین 10000 و 120000 میلی‌ثانیه؛ زمان monotonic شامل lookup، آماده‌سازی، انتظار اتصال، تراکنش، بازیابی و backoff است. |
| `balance.retry.backoff-millis` / `BALANCE_RETRY_BACKOFF_MILLIS` | 50 | پایهٔ ۰ تا ۱۰۰۰ میلی‌ثانیه؛ full jitter نمایی با پیش‌فرض‌ها ابتدا ۰ تا ۵۰ و سپس ۰ تا ۱۰۰ میلی‌ثانیه است. |
| timeout دریافت اتصال Hikari | 5000 ms | ورود به تراکنش تازه حداقل به مجموع مهلت اتصال و تراکنش، یعنی 10000 ms، نیاز دارد. |
| timeout و isolation تراکنش | 5 s، READ_COMMITTED | همان `TransactionTemplate` متعلق به coordinator. |
| timeout قفل SQL | 1000 ms | هنگام ساخت اتصال Hikari، دستور `SET LOCK_TIMEOUT 1000` اجرا می‌شود. |
| timeout کوئری native | 2000 ms | همان hintهای JPA در adapterهای حساب و عملیات. |
| timeout ورود/سوکت driver | 5 s / 5000 ms | propertyهای datasource در Hikari، در کنار تنظیمات صریح JDBC تست. |

تنظیم retry نامعتبر، startup را متوقف می‌کند. jitter بیرون از تراکنش است. پیش از انتظار، بیشترین jitter بعدی هم در budget حساب می‌شود؛ attemptی که مهلت کافی ندارد شروع نمی‌شود. این پیش‌فرض‌ها برای توسعهٔ محلی و deadline مشارکتی‌اند؛ تضمین سقف سخت شبکه/commit یا SLO اندازه‌گیری‌شده نیستند. تغییر دستی timeoutهای pool، driver یا تراکنش به بازبینی مهلت admission هم نیاز دارد.

## شواهد و محدودیت‌ها

اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۴۲۱ تست قابل‌حمل و ۱۵۷ تست واقعی SQL Server** را با **صفر failure، error و skip** در **43.636 ثانیه** گذراند. مجموع XMLهای تازهٔ Surefire/Failsafe بررسی شد.

تست‌های قابل‌حمل، کدهای vendor و cycle زنجیره، خطاهای غیرقابل retry، آماده‌سازی ثابت، rollback پیش از retry، بازیابی برندهٔ هم‌زمان، conflict، retry خواندن، exhaustion، budget، interrupt، شکست rollback، commit نامعلوم با نتیجهٔ موجود یا همیشه غایب و binding تنظیمات را بررسی می‌کنند. تست‌های قبلی credit/debit/transfer نیز رد ماندگار، replay و مرزهای نوشتن مالی را نگه می‌دارند.

اسکن syntax tree با compiler، همهٔ ۱۴۵ فایل Java را بررسی کرد و هیچ type تودرتو، local یا anonymous پیدا نکرد. لینک‌های نسبی اسناد تغییریافته و اسکن مقادیر دقیق credential محلی در logها و reportها هم موفق بودند.

سه تست تازهٔ SQL Server شواهد متفاوتی دارند:

- یک انتقال واقعی به حساب قفل‌شده می‌رسد و خطای واقعی 1222 می‌گیرد. هنگام backoff، SQL مستقل نبودن operation/journal/posting را بررسی می‌کند. بعد از آزادکردن قفل، attempt دوم با همان شیء آماده‌شده موفق می‌شود. دقیقاً یک operation، یک journal و دو posting commit می‌شوند و هر دو موجودی مستقل reconcile می‌شوند.
- یک fixture مستقل JDBC با دو اتصال، عمداً ترتیب قفل را معکوس می‌کند و تراکنش coordinator قربانی واقعی deadlock با کد 1205 می‌شود. نوشتن قبلی پیش از retry rollback می‌شود و مقدار نهایی فقط یک اثر دارد. این شاهد بازیابی coordinator/JDBC است؛ deadlock انتقال کامل JPA یا معکوس‌شدن قفل در مسیر تولیدی را ثابت نمی‌کند.
- wrapper مخصوص تست، بعد از commit واقعی انتقال خطا می‌دهد. بازیابی پاسخ ماندگار را برمی‌گرداند و replay از HTTP همان پاسخ را دارد. شمارش‌ها ۱/۱/۲ می‌مانند و موجودی‌ها reconcile می‌شوند. گم‌شدن تأیید شبیه‌سازی شده؛ قطع واقعی شبکه هنگام commit تزریق نشده است.

Docker Desktop ابتدا خاموش بود. اولین startup کانتینر موجود SQL Server با شواهد فشار حافظه شکست خورد. startup دوم همان کانتینر سلامت را برگرداند؛ image، volume، منابع، schema و grantها تغییر نکردند. اولین Maven در sandbox نتوانست lockهای cache را بنویسد؛ اجرای مجاز بعدی از همان cache استفاده کرد. regression اولیهٔ قابل‌حمل، سه assertion قدیمی چرخهٔ commit-failure را پیدا کرد؛ هر سه برای الزام تراکنش‌های تازهٔ بازیابی به‌روز شدند.

رقابت گستردهٔ duplicate/conflict بین instanceها، انتقال‌های معکوس، پیشرفت عملیات ترکیبی و faultهای بیشتر commit/response در CBS16–19 باز مانده‌اند. پذیرش مستقل یادگیری، احراز هویت/مالکیت و کارایی معیارهای جداگانه‌اند. شبیه‌سازی ARM64 محلی SQL Server خارج از پشتیبانی Microsoft است. migration، تغییر API عمومی، سرویس خارجی یا ادعای آمادگی production اضافه نشده است.
