# درخواست canonical و اثر انگشت حساب تأمین در CBS06

[English](../en/Canonical-request.md) · [پروتکل هویت](Idempotency-protocol.md) · [راهنمای عملیات](Operation-flow.md)

برای دلیل انتخاب‌ها، گزینه‌های دیگر، واژه‌های system design و timeline مثال‌ها، [راهنمای دلیل طراحی](Canonical-design.md) را بخوانید.

پایهٔ Portable در ۲۰۲۶-۱۰-۲۳ پیاده شده است. این سند قالب بایت نسخهٔ ۱ و API فعلی را تعریف می‌کند. پروتکل پیش‌نویس تراکنش/ذخیره‌سازی را نمی‌پذیرد و شناسه رزرو نمی‌کند، سیاست بارگذاری نمی‌کند، مجوز نمی‌دهد و replay اجرا نمی‌کند. adapter HTTP همچنان به fallback عدم دسترسی وصل است.

## چرا درخواست را canonical می‌کنیم؟

retry ممکن است فاصله و ترتیب فیلدهای JSON یا بزرگی حروف UUID را عوض کند اما همان قصد معتبر را داشته باشد. در مقابل، مبلغ یا مقصد جدید با شناسهٔ قبلی باید conflict بدهد. مقایسهٔ JSON خام این مرز را درست بیان نمی‌کند. canonicalization پس از parse روی command تایپ‌شده کار می‌کند و مقدار معنایی و نقش حساب را حفظ می‌کند.

شناسهٔ تراکنش کلید lookup است و جزو payload مقایسه‌شده نیست. دو شناسهٔ متفاوت می‌توانند بایت canonical یکسان و در عین حال دو تلاش مالی جدا باشند. این کد بین شناسه‌ها dedup نمی‌کند و قصد انسانی را حدس نمی‌زند. زمان transport، trace، قالب HTTP، timestamp پاسخ و راز OTP ورودی این کلاس‌ها نیستند.

## نوع‌های عمومی و مسئولیتشان

| نوع | مسئولیت |
| --- | --- |
| [CanonicalRequest](../../src/main/java/dev/kavrin/balance/operation/application/identity/CanonicalRequest.java) | ساخت هویت درخواست از commandهای credit/debit/transfer یا قصد مثبت افتتاحیهٔ داخلی |
| [CanonicalFingerprint](../../src/main/java/dev/kavrin/balance/operation/application/identity/CanonicalFingerprint.java) | بایت نسخه‌دار تغییرناپذیر، SHA-256 هگز کوچک و برابری مبتنی بر بایت |
| [FundingSelection](../../src/main/java/dev/kavrin/balance/operation/application/policy/FundingSelection.java) | AccountId حساب مقابل و نسخهٔ دقیق سیاست تأمین برای نگه‌داری کنار metadata اجرا |

factoryها وابستگی actor را با `Optional<String>` صریح می‌گیرند. `Optional.empty()` زمینهٔ محلی مورداعتماد فعلی را بدون هویت مشتری ساختگی نشان می‌دهد. actor حاضر باید غیرblank و Unicode معتبر باشد. trim، تبدیل بزرگی حروف و Unicode normalization نداریم. منبع آیندهٔ آن باید احراز هویت معتبر باشد، نه فیلد actor دلخواه JSON. این کد احراز هویت پیاده نمی‌کند.

شناسهٔ حساب، مقدار UUID parse‌شده با نمایش `UUID.toString()` است. پس حروف بزرگ و کوچک در UUID معتبر به یک هویت می‌رسند. نقش target/source/destination جدا می‌ماند و طبق ترتیب قفل sort نمی‌شود. مبلغ متن ده‌دهی canonical از long مثبت و ارز خروجی `Currency.getCurrencyCode()` است. این factoryها سیاست متغیر پشتیبانی ارز را نمی‌خوانند.

factory افتتاحیه قصد مثبت داخلی را نشان می‌دهد و برای funded fingerprint حساب مقابل متمایز می‌خواهد. endpoint جدید یا مقداردهی حساب پیاده نمی‌کند. حساب با موجودی صفر به قصد افتتاحیهٔ غیرصفر نیاز ندارد؛ این factory صفر و مبلغ منفی را رد می‌کند. نوع حساب، وجود واقعی counterpart و ذخیرهٔ journal مسئولیت مراحل بعد است.

## قالب ثابت نسخهٔ ۱

همهٔ headerهای عددی، صحیح علامت‌دار ۳۲بیتی با ترتیب big-endian هستند. بایت‌ها شامل این بخش‌ها هستند:

1. نسخهٔ قالب که اکنون `1` است.
2. تعداد فیلدها.
3. هر فیلد به ترتیب ثابت، با طول علامت‌دار و سپس بایت UTF-8. مقدار `-1` یعنی غایب و بایتی بعد از آن نیست. صفر یعنی متن حاضرِ خالی. طول، تعداد بایت است و نه تعداد کاراکتر جاوا.

درخواست هشت فیلد دارد:

| جایگاه | مقدار |
| --- | --- |
| ۱ | متن ثابت `request` |
| ۲ | نام enum برابر CREDIT، DEBIT، TRANSFER یا OPENING |
| ۳ | UUID هدف برای credit/debit/opening؛ برای transfer غایب |
| ۴ | UUID مبدأ transfer؛ در بقیه غایب |
| ۵ | UUID مقصد transfer؛ در بقیه غایب |
| ۶ | مبلغ مثبت با نمایش ده‌دهی canonical |
| ۷ | کد ارز |
| ۸ | شناسهٔ دقیق actor یا علامت غیاب |

طول صریح، ابهام `(a\|b, c)` و `(a, b\|c)` را از بین می‌برد و غیاب را از متن خالی جدا می‌کند. encoder، UTF-16 خراب مثل surrogate جفت‌نشده را به‌جای جایگزینی رد می‌کند تا دو هویت متفاوت به یک بایت تبدیل نشوند. فیلد معنایی actor/policy مقدار blank را هم رد می‌کند. delimiter، Unicode و متن غیرblank دارای فاصله همان‌طور که داده شده حفظ می‌شوند.

اجرای دارای تأمین چهار فیلد با همین framing نسخه‌دار دارد:

1. متن ثابت `funded-execution` برای جداسازی فضای نام از درخواست.
2. نمایش هگز کوچک **تمام بایت canonical درخواست**.
3. UUID حساب مقابل تأمین حفظ‌شده.
4. نسخهٔ دقیق و غیرblank سیاست تأمین.

قرار دادن کل درخواست داخلی، به‌جای صرفاً digest، مقایسهٔ مبتنی بر بایت را حفظ می‌کند. نسخهٔ ۱ از enum ordinal، encoding پیش‌فرض، hashCode آبجکت یا چیدمان JSON وابسته به serializer استفاده نمی‌کند. تغییر قالب نسخه و مرور سازگاری جدید می‌خواهد؛ نسخهٔ ۱ را پنهانی بازتعریف نکنید. decoder و dispatch نسخهٔ ذخیره‌شده هنوز کار persistence آینده است؛ این کلاس‌ها فعلاً فقط نسخهٔ ۱ می‌سازند.

`bytes()` کپی دفاعی می‌دهد. `equals` و `hashCode` از تمام بایت‌ها استفاده می‌کنند. `sha256()` digest کمکی پایدار است، نه توکن مجوز و نه جایگزین مقایسهٔ canonical. این مقدارها یکتایی ماندگار یا recovery برخورد hash پیاده نمی‌کنند.

## مثال انتقال

با import نوع‌های موجود AccountId، Money، command و java.util، مثال راهنمای عملیات چنین می‌شود:

```java
var command = new TransferCommand(
        new TransactionId("Tx-001"),
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
        new Money(100, Currency.getInstance("IRR")));

var request = CanonicalRequest.transfer(command, Optional.empty());
var fingerprint = request.fingerprint();
// fingerprint.version() == 1
// fingerprint.sha256():
// 9eac5bca4175ebd6aaa024fd362e313b1ae23cb7cf43e5f5fe4495b558a5a996
```

تست، بایت و hash دقیق را ثابت می‌کند؛ Python با UTF-8، packing از نوع big-endian و hashlib SHA-256 مستقل آن را بررسی کرده است. تغییر فقط Tx-001 به شناسهٔ معتبر دیگر fingerprint محتوای درخواست را عوض نمی‌کند. جابه‌جایی مبدأ/مقصد یا تغییر مبلغ، ارز، actor یا نوع عملیات آن را عوض می‌کند. transfer هر دو حساب مؤثر را از ابتدا دارد؛ fundedFingerprint برای آن خطا می‌دهد تا حساب سیستمی نامرتبط اضافه نشود.

## انتخاب حساب تأمین و ترتیب replay

در credit، debit و افتتاحیهٔ داخلی، درخواست مشتری حساب مقابل منتخب سرور را ندارد. به‌جای اعمال دوبارهٔ سیاست فعلی هنگام replay، دو مقایسه داریم:

```java
var credit = new CreditCommand(
        new TransactionId("credit-001"), customerAccountId,
        new Money(100, Currency.getInstance("IRR")));
var request = CanonicalRequest.credit(credit, Optional.empty());
var retainedSelection = new FundingSelection(fundingAccountId, "funding-v1");
var executionFingerprint = request.fundedFingerprint(retainedSelection);
```

فرض کنید شناسهٔ مشتری و تأمین دو مقدار تایپ‌شده و متمایزند. این خط‌ها فقط metadata می‌سازند. تغییر counterpart یا نسخهٔ سیاست، executionFingerprint را عوض می‌کند اما request.fingerprint() ثابت می‌ماند. credit، debit و opening جدا هستند چون درخواست داخلی نوع عملیات را دارد. counterpart برابر target رد می‌شود، اما factory نمی‌تواند وجود حساب، نوع حساب یا نقدینگی بیرونی را تضمین کند.

coordinator آینده ابتدا با شناسهٔ تراکنش رکورد حفظ‌شده را پیدا و fingerprint درخواست همراه بایت canonical را مقایسه می‌کند. برای هویت نهایی مطابق، پاسخ اصلی با metadata اجرای حفظ‌شده replay می‌شود؛ حساب تأمین امروز یا سیاست فعلی ارز دوباره انتخاب نمی‌شود. فقط برای عملیات جدید، counterpart و نسخهٔ سیاست انتخاب و حفظ می‌شوند، funded fingerprint ساخته می‌شود و پروتکل مرورشدهٔ رزرو/قفل/commit اجرا می‌شود. رد نهایی حفظ‌شده بدون posting هم درخواست/نتیجهٔ اصلی می‌خواهد؛ نبود حساب درخواستی نباید جلوی حفظ رد را بگیرد.

اگر funding-v2 به‌جای B حساب C را انتخاب کند، retry شناسهٔ قبلی باید B و پاسخ اصلی را نگه دارد. شناسهٔ جدید می‌تواند از C و funding-v2 استفاده کند. کلاس‌ها تفاوت را نشان می‌دهند، اما coordinator و persistence هنوز ترتیب را اجرا نمی‌کنند. selection برابر null، تأمین برای transfer و counterpart برابر target محلی رد می‌شوند.

## شواهد و محدودیت‌ها

[CanonicalRequestTests](../../src/test/java/dev/kavrin/balance/operation/application/identity/CanonicalRequestTests.java) درخواست معنایی یکسان با lookup ID متفاوت، بایت/hash ثابت نسخهٔ ۱، تغییر جهت/نوع/مبلغ/ارز/actor، برابری بزرگی حروف UUID، هویت دقیق actor، framing غیاب و delimiter، UTF-8 معتبر، کپی دفاعی، تغییر counterpart/نسخهٔ سیاست، افتتاحیه و ورودی لازم را بررسی می‌کند.

با Java 21:

```sh
./mvnw -Dtest=CanonicalRequestTests test
./mvnw verify
```

این‌ها تست خالص Portable هستند. مقایسه/collation واقعی SQL Server، decoder ماندگار، رزرو هویت، انتخاب سیاست مبتنی بر دیتابیس، replay واقعی، مجوز و اجرای مالی نداریم. مرور CBS02–04 پیش از migration و زیرساخت/harness در CBS07–08 همچنان لازم‌اند. کد نوشته‌شده با دستیار به معنای تسلط مستقل یا پذیرش رسمی نیست.

بررسی در ۲۰۲۶-۱۰-۲۳ با Java 21.0.11: اجرای کامل `./mvnw verify` در ۵٫۸۳۹ ثانیه ۲۲۳ تست را بدون failure، error یا skip گذراند؛ ۲۲ مورد canonical جدید است. تست‌ها پیش از افزودن پیاده‌سازی در compile شکست خوردند و سپس موفق شدند. تست SQL Server اجرا نشد.
