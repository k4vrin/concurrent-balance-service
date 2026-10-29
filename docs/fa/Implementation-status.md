# وضعیت پیاده‌سازی

## بازیابی محدود کل تراکنش در CBS15، ۲۰۲۶-۱۰-۲۹

credit، debit و transfer اکنون از `executeRecovering` مشترک استفاده می‌کنند: identity، سیاست و funding ثابت، طبقه‌بندی کد vendor پس از rollback گزارش‌شده، بازیابی تازهٔ همان ID و jitter محدود بیرون از تراکنش. پایان نامعلوم و گم‌شدن تأیید commit فقط خواندن بازیابی دارند؛ lookup خالی مجوز اجرای کور دوباره نیست. تنظیمات retry اعتبارسنجی می‌شوند؛ ساخت اتصال pool، timeout قفل و محدودیت login/socket در driver را اعمال می‌کند. [مسیر، تنظیمات، شواهد و محدودیت‌ها](Retry-execution.md).

اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۴۲۱ تست قابل‌حمل و ۱۵۷ تست واقعی SQL Server** را با **صفر failure، error و skip** در **43.636 ثانیه** گذراند. مجموع XMLهای تازهٔ Surefire/Failsafe بررسی شد.

سه تست واقعی تازه، rollback/retry انتقال با خطای واقعی 1222 و reconciliation، قربانی واقعی 1205 در fixture مستقل JDBC و گم‌شدن شبیه‌سازی‌شدهٔ تأیید پس از commit واقعی انتقال را می‌سنجند. رقابت‌های گستردهٔ duplicate/معکوس/ترکیبی بین instanceها و faultهای بیشتر پاسخ/commit در CBS16–19 می‌مانند. پذیرش مستقل یادگیری جداست. این checkpoint شامل پیاده‌سازی CBS15 و Javadoc سطح کلاس برای همهٔ typeهای Java تازه یا ویرایش‌شده است. checkpointهای پایین محدودهٔ شواهد تاریخی خود را دارند.


## اتصال مستقیم کنترلر به handlerها، ۲۰۲۶-۱۰-۲۶

interface موسوم به `BalanceOperations`، پیاده‌سازی `HandlerBalanceOperations` و factory به نام `stagedOperations` حذف شدند. `BalanceController` چهار handler را با پارامترهای Optional در سازنده می‌گیرد و متد `handle` هرکدام را مستقیم صدا می‌زند. Optional بیانگر نبود عمدی bean است: execution غیرفعال، استعلام موجودی را نگه می‌دارد و mutationها 503 می‌دهند؛ assembly مستقل از زیرساخت smoke هم برای همهٔ routeهای بدون handler همان 503 را می‌دهد. `HttpAdapterConfiguration` فقط parsing سخت‌گیرانهٔ JSON را تنظیم می‌کند. ObjectProvider یا سرویس forwarding جایگزین نداریم. مالکیت تراکنش در handler/coordinator مانده و خطای تراکنش خواندن موجودی همان نگاشت unavailable را دارد.

اجرای کامل Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۳۸۸ تست قابل‌حمل و ۱۵۴ تست واقعی SQL Server** را با صفر failure/error/skip در **37.050 ثانیه** گذراند. سه بررسی تازه، استعلام موجودی بدون handlerهای mutation، همهٔ routeهای mutation غیرفعال و نگاشت خطای تراکنش موجودی را می‌سنجند. تست‌های controller خود handlerها را mock می‌کنند. اجرای متمرکز web/response/smoke هر ۶۰ حالت را گذراند؛ اسکن syntax tree کامپایلر همچنان صفر نوع داخلی، محلی یا anonymous پیدا می‌کند. regression واقعی HTTP تا دیتابیس برای credit، debit، transfer و موجودی موفق بود. CBS15 و پذیرش مستقل یادگیری باز مانده‌اند. این checkpoint انتشار، تغییرات کامل انتقال، نوع‌های مستقل و اتصال مستقیم handlerها را ثبت می‌کند.


## نوع‌های مستقل پیاده‌سازی و regression، ۲۰۲۶-۱۰-۲۶

تمام سورس‌های Java تولید و تست مرور شدند و هر ۲۳ تعریف نوع داخلی، محلی یا anonymous که اسکن syntax tree کامپایلر پیدا کرد، حذف شدند. `LockedAccount` یک snapshot مشترک application است؛ `BalanceProjection`، `StoredOperation` و `OperationCompletion` نوع‌های مستقل persistence با دسترسی package-private هستند. `OperationSuccess`، `OperationRejected`، `OperationFailureReason` و `FundingProperties` هرکدام فایل خود را دارند. `HandlerBalanceOperations` جای dispatch ناشناس HTTP را گرفته است؛ recordهای حساب و افتتاحیه، datasource تست و transaction manager ثبت‌کنندهٔ مشترک هم مستقل شده‌اند. دستورالعمل مخزن اکنون استفاده از نوع‌های داخلی، محلی و anonymous را حتی در helperهای تست منع می‌کند.

اجرای کامل Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۳۸۵ تست قابل‌حمل و ۱۵۴ تست واقعی SQL Server** را با صفر failure/error/skip در **39.067 ثانیه** گذراند؛ مجموع XMLهای تازه و نتیجهٔ صفر اسکن syntax tree بررسی شدند. مالکیت تراکنش، قفل‌ها، ترتیب رزرو و نوشتن، schema، کلیدهای تنظیمات و قرارداد پاسخ HTTP حفظ شده‌اند. این نتیجه، شاهد regression سورس بازآرایی‌شده و تازه‌تر از اجرای قبلی CBS14 است. CBS15 و پذیرش مستقل یادگیری باز هستند.

در log ارسالی، URL اتصال همان `${DB_URL}` حل‌نشده بود. پردازش اجرا به `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` نیاز دارد؛ Spring Boot فایل env محلی ignored را خودکار بارگذاری نمی‌کند و اجرای جداگانهٔ JVM هم تنظیمات Compose را به ارث نمی‌برد. [راهنمای راه‌اندازی](Docker.md) روش رفع خطا را توضیح می‌دهد. دیتابیس جایگزین، credential یا dialect اجباری اضافه نشده است. تغییرات همچنان محلی و بدون commit هستند.


## انتقال اتمیک CBS14 و regression کامل SQL Server، ۲۰۲۶-۱۰-۲۶

[اجرای انتقال و شواهد](Transfer-execution.md) مسیر تازهٔ handler/store/encoder و اتصال route موجود HTTP را ثبت می‌کند. هر دو حساب مشتری با ترتیب UUID مشترک برنامه قفل می‌شوند. موفقیت، یک journal متوازن، دو posting، هر دو projection و پاسخ اصلی را با هم commit می‌کند؛ انتقال حساب یا metadata تأمین ندارد. retry یکسان پیش از سیاست فعلی، بایت‌های اصلی را replay می‌کند. نبود حساب، کسری وجه و عبور از سقف، تصمیم ماندگار بدون history دارند. انتقال به خود پیش از رزرو رد می‌شود.

اجرای کامل Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify`، تعداد **۳۸۵ تست قابل‌حمل و ۱۵۴ تست واقعی SQL Server** را با صفر failure/error/skip در **36.105 ثانیه** گذراند. ۳۴ تست قابل‌حمل تازه تصمیم‌ها، چرخهٔ handler و اعتبارسنجی و encoding پاسخ را می‌سنجند. ۲۰ تست واقعی تازه شامل موفقیت و replay روی HTTP، حفظ مجموع و reconciliation، نبود هرکدام از حساب‌ها، مرز دقیق سقف، محافظ overflow مقصد، conflict، بازسازی context/pool، timeout قفل و rollback پس از history، موجودی فقط مبدأ، هر دو موجودی و outcome همراه retry همان ID هستند. تست محافظ overflow، snapshot دامنه را تزریق می‌کند و history واقعی نزدیک BIGINT ندارد. شکست startup/pre-login و بازیابی با restart کانتینر بدون تغییر در راهنما ثبت شده‌اند.

هر چهار مسیر HTTP اکنون پیاده‌سازی دارند، اما retry/backoff عمومی، بازیابی commit نامعلوم و معیارهای گستردهٔ رقابت و عملیات ترکیبی باز هستند. کار مهندسی بعدی CBS15 است؛ پذیرش مستقل یادگیری تغییر نکرده است. تغییرات فعلاً محلی و بدون commit هستند. بخش‌های قبلی دامنهٔ تاریخی خود را دارند.


## debit ماندگار CBS13 و regression تازهٔ SQL Server، ۲۰۲۶-۱۰-۲۵

[اجرای debit و شواهد](Debit-execution.md) مسیر صریح handler/store/encoder و اتصال HTTP را ثبت می‌کند. برداشت دقیق موجودی موفق است؛ کسری وجه رد اصلی را بدون history commit می‌کند، حتی اگر credit بعدی وجه کافی فراهم کند. replay پیش از سیاست فعلی است و تعارض مبلغ/حساب/نوع عملیات با ID سراسری اثر اضافه ندارد. تاریخچه، projectionها و پاسخ ماندگار موفقیت در تراکنش coordinator مشترک‌اند. انتقال unavailable مانده و CBS14 آگاهانه خارج از این تغییر است.

اجرای کامل profile آفلاین SQL Server با Java 21، تعداد ۳۵۱ تست مستقل و ۱۳۴ تست واقعی SQL Server را در ۳۱٫۲۳۵ ثانیه با صفر failure/error/skip گذراند. پوشش جدید شامل ۲۶ حالت مستقل و ۱۴ حالت دیتابیس debit است: timeout واقعی حساب مسدود، rollback پس از نوشتن، retry همان ID، reconciliation مستقل و بازسازی pool/context. regressionهای موجود credit و binding نام‌دار و Tuple در AccountStore هم گذشتند. شکست‌های startup/pre-login در راهنمای debit ثبت شده‌اند؛ restart همان کانتینر بدون تغییر image، volume، تنظیمات یا permission سلامت را برگرداند. پذیرش رسمی یادگیری از این اجرا نتیجه نمی‌شود. retry/recovery جامع، auth و gateهای عملیات ترکیبی باز هستند.


## مستندات Journal و آماده‌سازی debit، ۲۰۲۶-۱۰-۲۵

برای `Journal`، سازنده و همهٔ helperهای projection، Javadoc اضافه شد. تست‌های قابل‌حمل تازه، projection برداشت ۱۰۰ ریال و برداشت کل موجودی ۵۰۰ ریال، رد موجودی نهایی منفی مشتری و تغییرنکردن snapshotهای ورودی را بررسی می‌کنند. `DebitAttempt` به‌عنوان record زمینهٔ اجرا اضافه و قالب `CreditAttempt` مرتب شد؛ handler، adapter ذخیره‌سازی یا اجرای HTTP مربوط به debit اضافه نشده است.

اجرای verify با Java 21 و Maven wrapper و گزینهٔ مستند پوشهٔ socket در workspace، هر ۳۲۵ تست قابل‌حمل را بدون failure، error یا skip در 42.065 ثانیه گذراند. تست integration مربوط به SQL Server به‌صراحت به ماشین دارای Docker موکول شده است؛ این گام محاسبات دامنه را بررسی می‌کند، نه debit ماندگار یا اتمی‌بودن دیتابیس را.

## خوانایی credit و نگاشت ذخیره‌سازی، ۲۰۲۶-۱۰-۲۵

کلاس `CreditHandler` به گام‌های روشن replay، آماده‌سازی، اجرا و بازیابی، و ساخت journal و projection بازآرایی شد. `JpaCreditStore` اکنون SQL را با text block و پارامترهای نام‌دار می‌نویسد، ردیف replay را با نام ستون‌ها به record خصوصی و نوع‌دار نگاشت می‌کند و برای وضعیت completion از switch کامل استفاده می‌کند. مسیر credit Javadoc دارد و هر دو [راهنمای کلاس‌ها](Credit-class-walkthrough.md) به‌روز شدند. این بازآرایی schema، ترتیب قفل‌ها، مرز تراکنش و ترتیب نوشتن و flush را حفظ می‌کند.

اجرای verify با Java 21 و Maven wrapper، با گزینهٔ مستند پوشهٔ Unix-domain socket در workspace، هر ۳۲۲ تست قابل‌حمل را بدون failure، error یا skip در 29.746 ثانیه گذراند؛ اجرای ساده به‌دلیل همان مشکل شناخته‌شدهٔ JDK ویندوز سه خطای smoke داشت. اجرای جداگانهٔ `SqlServerCreditIT` در آماده‌سازی fixture به‌دلیل نبود `SQLSERVER_INITIALIZER_TEST_URL` شکست خورد. هیچ مورد رگرسیون SQL Server واقعی برای این بازآرایی پاس نشده است؛ بررسی نگاشت native و binding پارامترها گام بعدی، پیش از کار روی debit و transfer، است. نتایج قبلی دیتابیس در ادامه شواهد تاریخی‌اند.

## پوشش مستقل CreditHandler، ۲۰۲۶-۱۰-۲۵

۱۶ مورد در `CreditHandlerTests` اضافه شد که بدون دیتابیس اجرا می‌شوند. handler، coordinator و محاسبات دامنه واقعی‌اند؛ رابط‌های ذخیره‌سازی و ساخت پاسخ mock هستند و یک transaction manager رویدادهای Spring را ثبت می‌کند. پوشش شامل موفقیت در سقف مانده، replay موفقیت و رد پیش از سیاست غیرفعال‌شدن ارز، تعارض یا خرابی replay، ارز پشتیبانی‌نشده برای درخواست تازه، سه رد نهایی، سرریز funding، خرابی در سه مرحلهٔ ذخیره‌سازی، خطای commit و بازیابی رزرو تکراری با نتیجهٔ قابل‌مشاهده یا بدون آن است. کد production و مجموعهٔ SQL Server تغییری نکردند. [گردش‌کار](Workflow.md) چرخه‌های مستقل و integration را توضیح می‌دهد.

در ویندوز با Java 21.0.12.1، فرمان نهایی هر ۳۲۲ تست را بدون failure، error یا skip در 25.489 ثانیه گذراند:

```powershell
.\mvnw.cmd -o -B -ntp "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/Projects/concurrent-balance-service/target" verify
```

تلاش نخست در sandbox اجازهٔ نوشتن قفل cache Maven را نداشت. نخستین اجرای مجموعه، دو انتظار نادرست در تست‌های تازه دربارهٔ علامت ماندهٔ funding با ماهیت بدهکار را آشکار کرد؛ هر دو اصلاح شدند. اجرای بعدی ۱۶ تست تازه را گذراند، اما سه تست smoke موجود به‌دلیل خطای اتصال محلی Unix-domain socket در JDK، هنگام ساخت context خطا دادند. انتخاب IPv4 مشکل را حل نکرد؛ تعیین پوشهٔ socket در workspace ابتدا سه تست smoke و سپس بررسی کامل را موفق کرد. هیچ تستی از اجرا حذف نشد. تست‌های integration مربوط به SQL Server برای این تغییر تست و مستندات اجرا نشدند. این شواهد، تصمیم‌های لایهٔ کاربرد و مدیریت چرخهٔ تراکنش Spring را بررسی می‌کنند؛ قفل، دوام یا rollback دیتابیس را ثابت نمی‌کنند. محدودیت‌های قبلی SQL Server در بخش‌های بعدی همچنان بازند.

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
