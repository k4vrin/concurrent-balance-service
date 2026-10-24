# نگاشت persistence، fixture افتتاحیه و خواندن موجودی

[English](../en/Persistence.md) · [schema و راه‌اندازی](Schema.md) · [projection سند](Journal-projection.md) · [API](API-v1.md) · [coordinator و قفل SQL](Operation-coordinator.md)

## رفتار پیاده‌شده

route موجودی اکنون projection مرجع حساب را از SQL Server می‌خواند. برای حساب موجود 200 با accountId/balanceRial/currency، برای حساب غایب 404 و هنگام کامل‌نشدن query دیتابیس، عدم دسترسی موقت می‌دهد. credit، debit و transfer تا آماده‌شدن handler ماندگار همان fallback صریح 503 هستند. migration تغییر نکرده است؛ Hibernate هنگام startup عادی نگاشت V1 موجود را validate می‌کند.

| جزء | مسئولیت |
| --- | --- |
| AccountEntity | نگاشت UUID، دسته، ارز، status و موجودی؛ فقط balance در UPDATE مربوط به ORM شرکت می‌کند. snapshot تغییرناپذیر دامنه از state قفل‌شده ساخته می‌شود. |
| OperationEntity | خواندن metadata درخواست، سیاست، تأمین و نتیجهٔ ذخیره‌شده؛ دسترسی دفاعی به byte array و نگاشت ORM تغییرناپذیر. |
| JournalEntity / PostingEntity / PostingId | خواندن header/line تغییرناپذیر با کلید ترکیبی journal/line؛ بدون رابطهٔ ORM، cascade یا API تغییر history. |
| AccountStore | query فقط projection و lookup بومی مرتب‌شده برای قفل حساب. |
| BalanceReader / GetBalanceHandler | port query برنامه و تراکنش read-only با READ_COMMITTED؛ نگاشت حساب غایب و unavailable. |
| OpeningFixture | افتتاحیهٔ داخلی اتمیک فقط در تست، با transaction manager واقعی JPA، محاسبهٔ دامنهٔ journal و اختیار runtime؛ bean تولید یا endpoint عمومی نیست. |

UUID به uniqueidentifier، ارز صریحاً به nchar(3)، متن Unicode ذخیره‌شده به nvarchar، hash به binary(32)، payload به varbinary(max) و زمان UTC دیتابیس به LocalDateTime نگاشت می‌شود. ستون زمانی طبق قرارداد UTC است؛ LocalDateTime به‌تنهایی timezone ندارد. شناسهٔ SQL و value type دامنه جدا می‌مانند. schema validation واقعی هنگام startup و round trip committed این انتخاب‌ها را می‌سنجند.

## مسیر خواندن

controller باریک موجود، AccountId را parse می‌کند، BalanceOperations.balance را می‌خواند و BalanceResponse می‌دهد. composition اکنون query را به GetBalanceHandler می‌سپارد و mutation همچنان unavailable می‌ماند. query فقط balance و currency را از AccountEntity انتخاب می‌کند. Account قابل‌نوشتن، journal، posting یا پاسخ operation بارگذاری نمی‌کند و هویت عملیات نمی‌سازد. timeout query دو ثانیه و تراکنش read-only پنج ثانیه است. context مستقل از زیرساخت با smoke همچنان persistence را کنار می‌گذارد و composition آن unavailable است.

در تنظیم محلی با RCSI غیرفعال، خواندن projection تغییرکرده ولی commit‌نشده منتظر می‌ماند و آن را نشان نمی‌دهد. تست تغییر موقت را در تراکنش اول نگه می‌دارد، HTTP GET واقعی را از اتصال دیگر می‌فرستد، پس از timeout پاسخ 503 می‌بیند و بعد از rollback موجودی صفر می‌خواند. این شاهد تنظیم تست‌شده است و ادعای همگانیِ خواندن بدون قفل نیست.

## مسیر قفل

AccountStore.lockAll تراکنش فعال قابل‌نوشتن JPA می‌خواهد. مجموعهٔ کامل حساب‌ها را deduplicate می‌کند، با AccountLockOrder.DESCENDING مرتب می‌کند و query نقطه‌ای بومی UPDLOCK/HOLDLOCK را به‌ترتیب اجرا می‌کند. ردیف غایب در map نتیجه نیست، ولی lookup محافظت range دیتابیس را نگه می‌دارد. تصمیم دربارهٔ رد نهایی حساب درخواستی یا نقص تنظیمات داخلی همچنان با caller است.

query بومی ممکن است entity موجود در persistence context را برگرداند. adapter پس از گرفتن قفل، entity مدیریت‌شده را refresh می‌کند و سپس snapshot را در اختیار می‌گذارد. تست تغییر state قدیمی به مقدار تازه، مسدودشدن نویسندهٔ بیرونی تا پایان تراکنش JPA و ترتیب مشترک بدون تکرار را نشان می‌دهد. همهٔ قفل‌ها پیش از تغییر projection مدیریت‌شده گرفته شوند؛ refresh وسیلهٔ حفظ ویرایش flush‌نشدهٔ قبلی نیست. سیاست کل تلاش، رزرو/replay و مدیریت retry/تنظیم session مسئولیت مرحلهٔ بعد coordinator می‌مانند.

## مثال مسیر افتتاحیه

تست ابتدا با credential مربوط به initializer دو حساب متمایز با موجودی صفر می‌سازد؛ مشتری C و تأمین مصنوعی F. سپس فقط با اختیار runtime و TransactionTemplate کنترل‌شده، C را با 150000 IRR افتتاح می‌کند:

1. transaction ID داخلی، UUID عملیات و UUID سند ساخته می‌شوند. CanonicalRequest.opening و FundingSelection با دو fingerprint نگه داشته می‌شوند.
2. operation خصوصی PENDING پیش از قفل حساب درج می‌شود. fixture تراکنش محیطی را رد می‌کند و سیاست اولیهٔ IRR و سقف مبلغ را رعایت می‌کند.
3. C/F به ترتیب مشترک قفل می‌شوند. مشتری فعال صفر و حساب funding فعال با دستهٔ درست لازم‌اند.
4. funding DEBIT برابر 150000 و customer CREDIT برابر 150000 ساخته می‌شود. Journal توازن دقیق و projectBalances همهٔ snapshotهای معتبر را پیش از تغییر آماده می‌کنند.
5. header و دو posting درج می‌شوند؛ projection هر دو از AccountEntity مدیریت‌شده اعمال و flush می‌شود. موجودی متناسب با سمت عادی هر دو دسته 150000 است.
6. موفقیت نهایی و پاسخ JSON داخلی ثبت می‌شوند؛ نتیجهٔ fixture فقط پس از تأیید commit در TransactionTemplate بیرون می‌آید.
7. نتیجه از persistence context تازه و route واقعی HTTP خوانده می‌شود. مجموع postingهای committed با decimal(38,0) مستقل محاسبه و با projection موردانتظار و journal متوازن مقایسه می‌شود.

fixture پس از journal، posting و flush موجودی خطای فنی تزریق می‌کند. هر بار رزرو عملیات، همهٔ history و هر دو projection rollback می‌شوند. حساب صفرِ ساخته‌شده پیش از تلاش می‌ماند. این شاهد مشارکت تراکنش fixture افتتاحیه است، نه موتور تولید کامل credit/replay.

fixture موفق history مصنوعی متوازن را در cbs_test commit می‌کند و اجرای دوباره آن را حفظ می‌کند. تست UUID تازه دارد و فقط رکورد خودش را می‌شمارد؛ history ذخیره‌شده clean یا حذف نمی‌شود. شاهد قبلی schema که عملیات صفر داشت تاریخی است. fixture شکست‌خورده history پولی اضافه نمی‌کند.

## شواهد و مرز بعدی

اجرای کامل Java 21.0.11 با `./mvnw -Psqlserver-it verify` در ۲۳٫۷۶۰ ثانیه، ۲۴۰ تست مستقل از زیرساخت و ۱۰۳ تست واقعی SQL Server را بدون failure، error یا skip گذراند. نُه مورد تازهٔ persistence/HTTP برابر ۶٫۰۲۴ ثانیه طول کشیدند. round trip هر چهار entity، schema validation، موجودی committed/صفر/غایب در HTTP، رد قفل خارج تراکنش یا در read-only، تازگی state، طول عمر قفل، جلوگیری از dirty read، مجموع دقیق افتتاحیه و سه مرز rollback بررسی شدند.

اجرای نخست نشان داد Spring، IllegalStateException در repository را به InvalidDataAccessApiUsageException تبدیل می‌کند؛ تست guard اکنون exception بیرونی واقعی و علت اصلی را می‌سنجد. گزارش نهایی موفق، اجرای ناموفق حین توسعه را جایگزین می‌کند. credential بیرون Git و دادهٔ fixture مصنوعی است. آزمایش SQL Server روی ARM64 همچنان پشتیبانی رسمی ندارد.

گام بعد slice ماندگار credit است؛ coordinator، snapshot سیاست/funding، reservation/replay/conflict، نگه‌داری پاسخ اصلی و موفقیت کامل HTTP تا دیتابیس. mutation عمومی، احراز هویت/مالکیت، retry کل تلاش، commit نامطمئن و reconciliation کامل عملیات ترکیبی باقی‌اند. پذیرش رسمی یادگیرنده از تست اجراشده توسط دستیار جداست.
