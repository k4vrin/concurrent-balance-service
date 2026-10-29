> به‌روزرسانی CBS15، ۲۰۲۶-۱۰-۲۹: mutationها اکنون [retry محدود کل تراکنش و بازیابی محافظه‌کارانهٔ نتیجهٔ نامعلوم](Retry-execution.md) دارند. عبارت‌های پایین دربارهٔ نبود retry مربوط به checkpoint قبلی‌اند. `execute` همچنان یک تراکنش اجرا می‌کند؛ handlerهای mutation از `executeRecovering` استفاده می‌کنند.

# اجرای ماندگار credit و پاسخ ذخیره‌شده

## assembly مربوط به smoke و وضعیت بررسی، ۲۰۲۶-۱۰-۲۵

همهٔ شرط‌های `@Profile("!smoke")` از production حذف شدند. `HttpSmokeConfiguration` مستقل در source تست فقط مرز HTTP را import می‌کند. تست حتی با execution فعال، نبودن beanهای مالی و persistence را بررسی می‌کند. تست coordinator و سیاست هم رفتار عادی dependency و property را مستقل از profile آزمایشی بررسی می‌کنند. [راهنمای کلاس‌ها](Credit-class-walkthrough.md) مسئولیت کلاس‌ها، funding، هویت و جریان موفقیت، خطا و replay را توضیح می‌دهد.

assembly اصلاح‌شده هر ۳۰۶ تست مستقل از زیرساخت را بدون failure، error یا skip گذراند. مرحلهٔ SQL Server کامل نشد؛ ابتدا environment صریح تست وجود نداشت و سپس اتصال تنظیم‌شده refused شد. container متوقف‌شدهٔ موجود بدون تغییر image، volume یا تنظیمات شروع شد ولی هنگام startup با status برابر 114 خارج شد. برای این تغییر pass فعلی SQL Server ادعا نمی‌شود؛ نتیجهٔ قبلی ۳۰۵+۱۲۰ پایین صفحه پیش از refactor مربوط به smoke است. تلاش نخست assembly به‌دلیل اضافه‌شدن برنامهٔ اصلی توسط `@TestConfiguration` شکست خورد و با `@Configuration` مستقل آزمایشی اصلاح شد. اجرای پیشین sandbox هم محدودیت attach مربوط به Mockito داشت. به درخواست کاربر، تست بیشتر متوقف شد. pass مستقل از زیرساخت اثبات runtime دیتابیس یا تکمیل gate مربوط به retry و concurrency نیست.

[English](../en/Credit-execution.md) · [Coordinator](Operation-coordinator.md) · [تنظیم سیاست](Execution-policy.md) · [Persistence](Persistence.md) · [API](API-v1.md)

[راهنمای کلاس‌ها و جریان‌ها](Credit-class-walkthrough.md)

در CBS11 و بخش نخست replay/conflict از CBS12، مسیر واقعی credit اضافه شده است. با `balance.execution.enabled=true` و ورودی معتبر سیاست، درخواست POST به `/api/v1/accounts/{accountId}/credits` روی SQL Server اجرا می‌شود. در حالت غیرفعال، credit همان fallback با 503 است. assembly مستقل HTTP smoke هم چون service مالی import نمی‌کند، همین fallback را دارد. debit و transfer هنوز unavailable هستند؛ query موجودی همان projection committed را می‌خواند. کاربرد فعلی API دسترسی محلی قابل‌اعتماد است؛ احراز هویت و مالکیت پیاده نشده‌اند.

## دنبال‌کردن credit صدریالی

فرض کنید مشتری C با موجودی صفر و status فعال وجود دارد و حساب مصنوعی FUNDING_CLEARING با شناسهٔ F جداگانه و با ارز IRR آماده شده است. FundingSelection تنظیم‌شده F را انتخاب می‌کند. caller فقط C، مبلغ، ارز و transactionId را می‌فرستد. ایجاد عمومی حساب یا تأمین واقعی از بانک خارجی در این مسیر وجود ندارد.

| جزء | مسئولیت در درخواست |
| --- | --- |
| AmountRequest / CreditCommand | parse دقیق HTTP و شکل تغییرناپذیر درخواست با مبلغ مثبت |
| CanonicalRequest / CanonicalFingerprint | نیت نسخه‌دار caller، مستقل از کلید lookup و سیاست فعلی |
| ExecutionPolicy / AmountLimits / FundingSelection | snapshot startup، سقف شامل مقدار مساوی و حساب مقابل سرور |
| CreditHandler / CreditStore | جریان application و port مربوط به persistence |
| OperationCoordinator | مالکیت تراکنش REQUIRED/READ_COMMITTED، رد تراکنش فعال و بازگشت پس از commit |
| JpaCreditStore / AccountStore | رزرو، lookup ذخیره‌شده، قفل بومی مرتب و نوشتن SQL/JPA |
| Journal / Posting / Account | history متوازن و محاسبهٔ checked همهٔ projectionها |
| CreditResponseEncoder / JsonCreditResponseEncoder | serialize پاسخ عمومی اصلی پیش از تکمیل |
| StoredResponse / MutationResult / StoredOperationResponse | کپی دفاعی بایت‌ها، نتیجهٔ application و تحویل دقیق HTTP |

1. درخواست credit با actor صریحاً غایب برای دامنهٔ محلی فعلی canonical می‌شود. در تراکنش متعلق به coordinator، transaction ID پیش از ارز، سقف یا funding فعلی lookup می‌شود. بایت کامل canonical مقایسه می‌شود؛ SHA-256 به‌تنهایی مجوز replay نیست. format ناشناخته، PENDING committed یا نتیجهٔ ناسازگار خطای فنی می‌دهند.
2. اگر نتیجهٔ نهایی وجود داشته باشد، همان outcome و پاسخ برمی‌گردد. نیت canonical متفاوت، 409 با TRANSACTION_ID_CONFLICT می‌دهد و اثر تازه‌ای ندارد. نتیجهٔ قبلی پس از تغییر سیاست یا حساب دوباره اجرا نمی‌شود.
3. برای شناسهٔ تازه، ارز فعلی غیرمجاز با 400 و بدون operation رد می‌شود. funding انتخاب و UUID عملیات و journal ساخته می‌شوند. funding باید با مشتری متفاوت باشد. snapshot تغییرناپذیر startup برای این تلاش می‌ماند؛ hot reload یا حلقهٔ عمومی retry نداریم.
4. تراکنش نوشتن شروع و شناسهٔ عمومی پیش از قفل حساب رزرو می‌شود. برخورد 2627/2601 فقط برای کلید نام‌دار `uq_operations_transaction_id` از این تراکنش خارج می‌شود. پس از rollback، lookup تازهٔ نتیجهٔ committed انجام می‌شود. خطای integrity دیگر duplicate محسوب نمی‌شود.
5. مبلغ بالاتر از سقف تراکنش، TRANSACTION_LIMIT_EXCEEDED را بدون journal یا projection commit می‌کند. این بررسی پیش از lookup حساب است و بر رد حساب غایب تقدم دارد. در غیر این صورت مجموعهٔ کامل C/F بدون تکرار و با ترتیب نزولی UUID برنامه، با UPDLOCK/HOLDLOCK قفل می‌شود.
6. وجود، status فعال، دسته و ارز funding داخلی بررسی می‌شود. funding نامعتبر نقص فنی است و rollback می‌کند. مشتری غایب، ACCOUNT_NOT_FOUND را بدون نوشتن پولی commit می‌کند. حساب موجودِ غیرفعال، با دستهٔ نادرست یا ارز ناسازگار فعلاً خطای فنی می‌دهد؛ کد رد نهاییِ مرور‌نشده‌ای اختراع نشده است.
7. پیش از جمع، ظرفیت موجودی مشتری بررسی می‌شود: اگر `current > customerCap - amount` باشد، BALANCE_LIMIT_EXCEEDED ثبت می‌شود. این مقایسه با این ورودی‌ها جمع overflowشونده نمی‌سازد؛ مقدار مساوی مجاز است. سپس funding DEBIT برابر 100 و customer CREDIT برابر 100 ساخته می‌شود و Journal.projectBalances اجرا می‌شود. overflow حساب funding همچنان خطای فنی و rollback است، نه موفقیت یا رد ذخیره‌شده.
8. یک header و دو line تغییرناپذیر درج، هر دو projection مدیریت‌شده اعمال و flush می‌شوند. پاسخ موفق serialize و envelope دقیق version/status/content-type/body همراه operation نهایی ذخیره و سپس commit می‌شود. همهٔ نوشتن‌ها در یک تراکنش فیزیکی‌اند. تا اعتبارسنجی همهٔ projectionها هیچ تغییر پولی اعمال نمی‌شود. رد تجاری فقط نتیجه و پاسخ خود را ذخیره می‌کند.
9. پس از commit موفق، بایت‌های اصلی تحویل می‌شوند. با قرارداد موجودی متناسب با سمت عادی دسته‌ها، C صد واحد زیاد می‌شود و DEBIT هم F را صد واحد زیاد می‌کند. برابری debit/credit سند را متوازن می‌کند؛ funding معادل موجودی حساب بانکی بیرونی یا شاهد ورود وجه نیست.

## حفظ پاسخ عمومی اصلی

سازندهٔ دوآرگومانی MutationResult برای نتیجهٔ unwired یا test double حفظ شده و StoredResponse اختیاری اضافه شده است. envelope کپی دفاعی بایت‌ها را نگه می‌دارد و format 1 با موفقیت `200` با `application/json` یا رد نهایی `4xx` با `application/problem+json` را می‌پذیرد. JsonCreditResponseEncoder از mapper عمومی استفاده می‌کند، instance مربوط به مسیر credit را در رد نگه می‌دارد و JSON بازیابی‌شده را با metadata عملیات تطبیق می‌دهد.

نوع sealed یعنی OperationResponseBody اکنون StoredOperationResponse را هم مجاز می‌داند. serialize با RawValue همان JSON ذخیره‌شده را تحویل می‌دهد و پاسخ را با قواعد mapper فعلی بازسازی نمی‌کند. در تست واقعی HTTP، پاسخ نخست و replay ترتیبی بایت‌به‌بایت با دیتابیس مقایسه می‌شوند. status، ارز، reason و instance پس از تغییر سیاست حفظ می‌شوند. مقایسه و hashCode مربوط به StoredResponse محتوای بایت‌ها را بین بارگذاری‌های مستقل مقایسه می‌کنند. تست مستقل codec، JSON نامعتبر و metadata/outcome ناسازگار را رد می‌کند. schema و migration نسخهٔ V1 تغییر نکرده‌اند.

## شکست، محدودیت زمان و recovery باقی‌مانده

coordinator اکنون timeout تراکنش پنج‌ثانیه‌ای دارد؛ queryهای بومی credit و حساب timeout دوثانیه‌ای دارند. این‌ها کنترل statement و تراکنش‌اند و deadline قطعی کل درخواست نیستند. exception دیتابیس یا تراکنش پس از cleanup به 503 با OPERATION_UNAVAILABLE نگاشت می‌شود و علت داخلی حفظ می‌شود؛ متن خام SQL به client نمی‌رود. خطای invariant یا overflow غیرمنتظره فنی می‌ماند و معمولاً advice موجود آن را به 500 تبدیل می‌کند. timeout، خطای اتصال، 500/503 یا پاسخ گم‌شده به recovery با همان transaction ID نیاز دارد.

فقط برخورد شناسهٔ موردانتظار فعلاً recovery خودکار تازه را آغاز می‌کند. retry/backoff عمومی قربانی deadlock، حلقهٔ بازیابی commit نامطمئن، بودجهٔ کل درخواست یا اعمال تنظیم lock-wait در session هنوز نداریم. gate کامل concurrency و reconciliation عملیات ترکیبی باز است. یک race موفق تضمین production نیست.

## شواهد اجرا، ۲۰۲۶-۱۰-۲۴

اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify` در ۲۶٫۸۴۰ ثانیه، ۳۰۵ تست مستقل از زیرساخت و ۱۲۰ تست SQL Server را بدون failure، error یا skip گذراند. چهارده مورد تازهٔ credit شامل موفقیت/replay/conflict واقعی HTTP، توازن decimal سند و بازسازی مشتری/funding، حفظ رد پس از تغییر سیاست یا حساب، عدم رزرو ارز غیرمجاز، نقص funding و rollback پس از نوشتن history/projection/outcome است.

یک تست context واقعی برنامه و pool مربوط به Hikari را می‌بندد، context/pool دیگری می‌سازد و نتیجهٔ committed را replay می‌کند؛ این تست restart در JVM یا سرور جدا نیست. یک race با دو worker هماهنگ، برخورد کلید نام‌دار و فقط یک operation/journal و دو posting را نشان می‌دهد. gate کامل چند instance یا عملیات ترکیبی را ثابت نمی‌کند.

تست overflow فقط snapshot دامنهٔ funding قفل‌شده را با Long.MAX_VALUE در تراکنش واقعی رزرو جایگزین می‌کند و rollback کامل را می‌سنجد. موجودی setup ناسازگار commit نمی‌کند و شاهد overflow محاسباتی SQL نیست. assertionهای نخست تزریق خطا انتظار exception داخلی مستقیم داشتند؛ Spring آن را در مرز repository ترجمه کرد. assertion نهایی، unavailable و علت اصلی تزریق‌شده و رکوردها/موجودی بدون تغییر را بررسی می‌کند.

تست‌ها فقط cbs_test، identityهای محدود و صریح runtime/initializer، شناسهٔ یکتا و history مصنوعی متوازنِ حفظ‌شده دارند. پاک‌سازی history، migration تازه یا grant گسترده اضافه نشده است. SQL Server همان آزمایش emulation روی ARM64 بدون پشتیبانی رسمی است. پذیرش رسمی کار و تسلط مستقل یادگیرنده هنوز ارزیابی نشده‌اند. گام بعد sliceهای debit/transfer و شواهد باقی‌ماندهٔ retry، شکست و عملیات ترکیبی است.
