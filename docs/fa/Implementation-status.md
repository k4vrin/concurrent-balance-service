# وضعیت پیاده‌سازی

## assembly مربوط به smoke و وضعیت بررسی، ۲۰۲۶-۱۰-۲۵

همهٔ شرط‌های `@Profile("!smoke")` از production حذف شدند. `HttpSmokeConfiguration` مستقل در source تست فقط مرز HTTP را import می‌کند. تست حتی با execution فعال، نبودن beanهای مالی و persistence را بررسی می‌کند. تست coordinator و سیاست هم رفتار عادی dependency و property را مستقل از profile آزمایشی بررسی می‌کنند. [راهنمای کلاس‌ها](Credit-class-walkthrough.md) مسئولیت کلاس‌ها، funding، هویت و جریان موفقیت، خطا و replay را توضیح می‌دهد.

assembly اصلاح‌شده هر ۳۰۶ تست مستقل از زیرساخت را بدون failure، error یا skip گذراند. مرحلهٔ SQL Server کامل نشد؛ ابتدا environment صریح تست وجود نداشت و سپس اتصال تنظیم‌شده refused شد. container متوقف‌شدهٔ موجود بدون تغییر image، volume یا تنظیمات شروع شد ولی هنگام startup با status برابر 114 خارج شد. برای این تغییر pass فعلی SQL Server ادعا نمی‌شود؛ نتیجهٔ قبلی ۳۰۵+۱۲۰ پایین صفحه پیش از refactor مربوط به smoke است. تلاش نخست assembly به‌دلیل اضافه‌شدن برنامهٔ اصلی توسط `@TestConfiguration` شکست خورد و با `@Configuration` مستقل آزمایشی اصلاح شد. اجرای پیشین sandbox هم محدودیت attach مربوط به Mockito داشت. به درخواست کاربر، تست بیشتر متوقف شد. pass مستقل از زیرساخت اثبات runtime دیتابیس یا تکمیل gate مربوط به retry و concurrency نیست.

README عمومی قرارداد نسخهٔ اول را شرح می‌دهد. نسخهٔ فعلی schema چهار جدول، نگاشت JPA، خواندن موجودی committed از SQL Server و credit ماندگار اختیاری دارد. فعال‌سازی credit به balance.execution.enabled=true و تنظیم معتبر funding نیاز دارد؛ debit و transfer هنوز unavailable هستند. دامنهٔ دسترسی همچنان محلی و قابل‌اعتماد است.

آخرین اجرای کامل در ۲۶٫۸۴۰ ثانیه، ۳۰۵ تست مستقل از زیرساخت و ۱۲۰ تست واقعی SQL Server را بدون failure، error یا skip گذراند. [شواهد اجرای credit و replay](Credit-execution.md) history/projection/outcome اتمیک، پاسخ بایت‌به‌بایت، رد نهایی، بازسازی context/pool، یک race رزرو و rollback تزریق‌شده را توضیح می‌دهد. [شواهد persistence و افتتاحیه](Persistence.md) و [schema و راهنما](Schema.md) برای راه‌اندازی در دسترس‌اند.

گام بعد sliceهای debit/transfer و gateهای باقی‌ماندهٔ شکست، retry و عملیات ترکیبی است. retry/backoff عمومی، recovery مربوط به commit نامطمئن، احراز هویت و مالکیت هنوز پیاده نشده‌اند. افتتاحیه فقط تستی است. بخش‌های checkpoint قبلی شاهد تاریخی‌اند؛ هر جا پیاده‌سازی جلو رفته، خلاصهٔ فعلی جای وضعیت قبلی را می‌گیرد.

## پایهٔ coordinator، ۲۰۲۶-۱۰-۲۴

[راهنمای طراحی و مثال قفل SQL Server](Operation-coordinator.md) مالکیت تراکنش، تزریق manager، انتخاب isolation و UPDLOCK/HOLDLOCK را شرح می‌دهد.

کلاس `OperationCoordinator` اکنون با دریافت `PlatformTransactionManager` در سازنده و یک `TransactionTemplate` خصوصی، مالک تراکنش قابل‌نوشتن با isolation برابر `READ_COMMITTED` است. تراکنش فعال فراخواننده را رد می‌کند، خطای فنی را به بیرون منتقل می‌کند و نتیجهٔ callback را فقط پس از پایان موفق template برمی‌گرداند. این کلاس هنوز به handler مربوط به credit متصل نیست و رزرو شناسه، ذخیرهٔ نتیجه یا retry انجام نمی‌دهد.

پنج تست مستقل از زیرساخت، چرخهٔ تراکنش Spring را با یک transaction manager ثبت‌کننده بررسی می‌کنند: isolation و قابلیت نوشتن و بازگشت پس از commit، rollback هنگام خطای اجرای callback، انتقال خطای commit و رد تراکنش فعال قابل‌نوشتن یا فقط‌خواندنی پیش از اجرای callback. این تست‌ها شاهد commit یا rollback در SQL Server نیستند. کامپایل اولیهٔ تست‌ها به‌دلیل نبود کلاس coordinator شکست خورد؛ پس از پیاده‌سازی، دستور `./mvnw -o -B -ntp verify` با Java 21 هر ۲۴۵ تست را بدون failure، error یا skip گذراند. تست‌های یکپارچهٔ SQL Server در این گام دوباره اجرا نشدند؛ نتیجهٔ قبلی ۱۰۳ تست، شاهد تاریخی باقی می‌ماند.

## مرحلهٔ اتصال Spring، ۲۰۲۶-۱۰-۲۴

کلاس `OperationCoordinator` اکنون مانند اجزای persistence با `@Service` و `@Profile("!smoke")` ثبت می‌شود. در context عادی، Spring مدیر تراکنش را به سازنده تزریق می‌کند؛ context فقط‌آزمایشی smoke چون زیرساخت persistence ندارد، coordinator را کنار می‌گذارد. اتصال handler مربوط به credit هنوز باقی است. دو تست context تازه تزریق manager و کنارگذاشتن در smoke را بررسی می‌کنند. اجرای کامل Java 21 با `./mvnw -o -B -ntp verify` در ۸٫۲۱۹ ثانیه هر ۲۴۷ تست را بدون failure، error یا skip گذراند.

سه مورد تازه در `SqlServerPersistenceIT` برای مشارکت JPA و READ_COMMITTED و مشاهدهٔ probe پس از commit، مشاهدهٔ rollback از اتصال تازهٔ JDBC و رد تراکنش فعال JPA نوشته شده‌اند. از fixture موجود با جدول موقت و نام یکتا استفاده می‌کنند و history مالی نمی‌نویسند. این موارد هنوز تأیید نشده‌اند: تلاش نخست هنگام setup با connection refused شکست خورد؛ راه‌اندازی مجدد container موجود هم با خطای تخصیص page و خطاهای 17300/17312 در SQL Server ناموفق بود. اجرای کامل profile نیز هنگام setup دیتابیس ۱۷ error داشت؛ این نتیجه به‌معنای قبولی تست دیتابیس نیست. container با کد 235 خارج شد؛ volume حذف نشد و schema یا تنظیم منابع Docker تغییر نکرد. شاهد تاریخی ۱۰۳ تست، این گام را تأیید نمی‌کند. پس از بازیابی محیط محلی SQL Server باید `./mvnw -Psqlserver-it verify` دوباره اجرا شود تا بتوان اتصال واقعی JPA را پذیرفته دانست.

## اجرای موفق دوبارهٔ دیتابیس، ۲۰۲۶-۱۰-۲۴

تست‌های coordinator/JPA بالا اکنون تأیید شده‌اند. SQL Server داخل container سالم بود، ولی Docker با وجود تنظیم حفظ‌شدهٔ 127.0.0.1:14333، هیچ port فعال منتشر نکرده بود. restart همان container نگاشت را برگرداند؛ image، volume، schema یا تنظیم منابع تغییر نکرد. این نقص اتصال با شکست قبلی تخصیص حافظه متفاوت است و رفع آن علت شکست حافظه را مشخص نمی‌کند.

اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify` در ۲۷٫۷۰۶ ثانیه، ۲۴۷ تست مستقل از زیرساخت و ۱۰۶ تست SQL Server را بدون failure، error یا skip گذراند. مجموع گزارش‌های XML تازه بررسی شد. هر سه مورد تازه موفق بودند: manager تزریق‌شده از نوع JpaTransactionManager است، EntityManager به تراکنش READ_COMMITTED می‌پیوندد، اتصال دیگر probe committed را می‌بیند، probe ناموفق rollback می‌شود و تراکنش فعال JPA پیش از اجرای کار رد می‌شود. probe موقت جدول تولیدشدهٔ خودش را پاک می‌کند؛ تست عادی افتتاحیه history مصنوعی committed را نگه می‌دارد. هیچ رمز تنظیم‌شده‌ای در log نهایی یا گزارش‌ها نبود. وضعیت تأییدنشدن بالا با این نتیجه جایگزین می‌شود، ولی سابقهٔ شکست حفظ می‌ماند. اجرای credit، replay/recovery و پذیرش رسمی یادگیرنده هنوز باقی‌اند؛ اجرای ARM64 همچنان پشتیبانی رسمی ندارد.

## ورودی‌های سیاست CBS11، ۲۰۲۶-۱۰-۲۴

پس از انتشار commit مربوط به coordinator با شناسهٔ `386fe8c`، دو نوع مستقل از framework یعنی `AmountLimits` و `ExecutionPolicy` اضافه شدند؛ سقف مستقل تراکنش و مشتری با پذیرش مقدار مساوی، mapping تغییرناپذیر ارز به funding و نسخه‌های مستقل اجرا و funding. کمبود یا mapping اضافی هنگام ساخت snapshot رد می‌شود؛ وجود، status، دسته و ارز حساب باید در handler قفل‌شده بررسی شوند. جزئیات در [راهنمای سیاست اجرا](Execution-policy.md) است. loader تنظیمات، رد ماندگار سقف، نوشتن credit یا replay اضافه نشده‌اند.

کامپایل اولیهٔ تست‌ها پیش از وجود نوع‌ها شکست خورد. اجرای Java 21 با `./mvnw -o -B -ntp verify` در ۸٫۵۲۳ ثانیه ۲۶۶ تست مستقل از زیرساخت را بدون failure، error یا skip گذراند؛ ۱۹ مورد تازه‌اند. تست SQL Server برای این value typeهای هنوز متصل‌نشده تکرار نشد؛ اجرای قبلی ۱۰۶ تست متعلق به مرحلهٔ coordinator است. گام بعد بارگذاری سیاست بیرونی و رزرو، حسابداری و نتیجه/replay ماندگار credit است.

## جداسازی اعتبارسنجی هویت، ۲۰۲۶-۱۰-۲۴

طبق درخواست مرور، وابستگی ExecutionPolicy به CanonicalFingerprint حذف شد. بررسی تکرارشوندهٔ موجودبودن، غیرخالی‌بودن و Unicode معتبر اکنون در utility داخلی پکیج IdentityText است و نسخهٔ اجرا، نسخهٔ funding و actor اختیاری از آن استفاده می‌کنند. encoding مربوط به fingerprint همچنان دقیق است و قرارداد بایت/hash تغییر نکرد. یازده تست، Unicode معتبر شامل surrogate pair، surrogate نامعتبر، متن خالی و null را پوشش می‌دهند. کامپایل اولیه پیش از وجود utility شکست خورد؛ سپس verify مستقل از زیرساخت با Java 21 هر ۲۷۷ تست، از جمله بردارهای canonical موجود را بدون failure، error یا skip در ۷٫۱۸۴ ثانیه گذراند. اجرای SQL Server یا رفتار مالی runtime تازه‌ای ادعا نمی‌شود.

## بارگذاری typed سیاست در CBS11، ۲۰۲۶-۱۰-۲۴

پس از انتشار مرحلهٔ سیاست و هویت با commit `a13a54c`، دو کلاس ExecutionPolicyProperties و ExecutionPolicyConfiguration اضافه شدند. بیرون از smoke و فقط با فعال‌سازی صریح، تنظیم typed یک ExecutionPolicy معتبر و تغییرناپذیر در startup می‌سازد. پیش‌فرض اولیه execution-v1، ارز IRR و هر دو سقف پذیرفته‌شدهٔ 10000000000000 است؛ UUID و نسخهٔ funding باید داده شوند. YAML برنامه و .env.example ورودی‌های محیطی IRR را شرح می‌دهند. تنظیم فعال نامعتبر startup را متوقف می‌کند؛ loader حساب اختراع نمی‌کند و آن را در دیتابیس validate نمی‌کند. جزئیات در [تنظیم سیاست](Execution-policy.md) است.

چهارده تست context و binding تازه پیش‌فرض، override، ورودی غایب/نامعتبر، کنارگذاشتن smoke و placeholder واقعی YAML را بررسی می‌کنند. کامپایل نخست پیش از وجود adapter شکست خورد. اجرای نهایی Java 21 با ./mvnw -o -B -ntp -Psqlserver-it verify در ۲۱٫۶۷۳ ثانیه، ۲۹۱ تست مستقل از زیرساخت و ۱۰۶ تست SQL Server را بدون failure، error یا skip گذراند. regression دیتابیس از سیاست غیرفعال پیش‌فرض استفاده می‌کند؛ binding فعال شاهد context مستقل از زیرساخت است. اجرای عمومی credit، رزرو، نتیجهٔ ذخیره‌شده/replay یا hot reload هنوز موجود نیست. گام بعد تراکنش ماندگار credit و پاسخ ذخیره‌شده است.

## credit ماندگار و بخش نخست replay/conflict، ۲۰۲۶-۱۰-۲۴

پس از انتشار تنظیمات با commit 1c51748، CreditHandler/CreditStore/JpaCreditStore و نگه‌داری پاسخ اصلی پیاده شدند. فعال‌سازی صریح سیاست اکنون credit عمومی را فعال می‌کند. یک تراکنش متعلق به coordinator پیش از قفل مرتب حساب شناسه را رزرو می‌کند، همهٔ projectionها را checked اعتبارسنجی و journal/posting، هر دو موجودی و پاسخ نهایی را با هم commit می‌کند. replay نیت کامل canonical را پیش از سیاست فعلی مقایسه می‌کند؛ برخورد کلید شناسه از تراکنش ناموفق خارج و سپس recovery تازه انجام می‌شود. رد سقف یا حساب غایب بدون نوشتن پولی commit می‌شود. HTTP بایت موفقیت/problem اصلی را با خانوادهٔ sealed توسعه‌یافته برمی‌گرداند. debit/transfer هنوز unavailable هستند.

اجرای نهایی Java 21 با ./mvnw -o -B -ntp -Psqlserver-it verify در ۲۶٫۸۴۰ ثانیه، ۳۰۵ تست مستقل از زیرساخت و ۱۲۰ تست SQL Server را بدون failure، error یا skip گذراند. چهارده مورد واقعی credit شامل HTTP replay/conflict، حفظ رد سقف/حساب غایب، بازسازی context/pool، یک race یکتایی، تعداد/مجموع دقیق حسابداری و مرزهای rollback است. تست overflow funding یک snapshot دامنه را داخل تراکنش رزرو واقعی تزریق می‌کند و شاهد SQL overflow نیست. سه assertion نخست تزریق خطا برای ترجمهٔ exception توسط repository اصلاح شدند و اکنون علت اصلی را حفظ و بررسی می‌کنند. recovery عمومی شکست گذرا/commit نامطمئن و gate عملیات ترکیبی باقی‌اند. جزئیات در Credit-execution.md است. کار تازه پس از push درخواستی محلی مانده و پذیرش یادگیرنده تغییر نکرده است.
