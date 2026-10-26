# نتیجهٔ کسب‌وکار و مرز commit

[English](../en/Operation-outcomes.md) · [پروتکل تراکنش](Transaction-protocol.md) · [قرارداد HTTP](API-v1.md)

برای مثال کامل از درخواست تا پاسخ و توضیح فایل‌ها، [راهنمای جریان عملیات](Operation-flow.md) را بخوانید.

`OperationOutcome` یک sealed interface مستقل از فریم‌ورک با دو record تغییرناپذیر است. تصمیم نهایی کسب‌وکار را نمایش می‌دهد. ساخت آن عملیات را اجرا نمی‌کند و commit دیتابیس را ثابت نمی‌کند.

| نوع | فیلدها | معنا |
| --- | --- | --- |
| `OperationSuccess` | TransactionId و OperationType | تلاش نتیجهٔ موفق را انتخاب کرده؛ انتشار آن فقط پس از تأیید commit مجاز است |
| `OperationRejected` | TransactionId و OperationType و RejectionReason | رد نهایی کسب‌وکار که باید بدون نوشتن مالی commit شود |

OperationType فعلاً CREDIT، DEBIT، TRANSFER و OPENING داخلی را دارد. OPENING برای طراحی پذیرفته‌شدهٔ افتتاحیهٔ سنددار است؛ enum به‌تنهایی handler افتتاحیه پیاده نمی‌کند یا endpoint عمومی اضافه نمی‌کند. فیلدهای لازم null را رد می‌کنند. Success دلیل رد ندارد و Rejected بدون دلیل ساخته نمی‌شود. sealed بودن امکان switch کامل در Java 21 را می‌دهد و نوع نتیجهٔ دلخواه تازه را محدود می‌کند.

## مثال کسری موجودی

یک debit با ساختار معتبر ۱۰۰ ریال می‌خواهد ولی فقط ۵۰ موجود است. پس از تعیین هویت و بررسی موجودی با پروتکل قفل آینده، ارزیابی می‌تواند این مقدار را برگرداند:

```java
OperationOutcome outcome = new OperationRejected(
        new TransactionId("debit-001"),
        OperationType.DEBIT,
        RejectionReason.INSUFFICIENT_FUNDS
);
```

دلیل‌های فعلی ACCOUNT_NOT_FOUND، INSUFFICIENT_FUNDS و BALANCE_OVERFLOW هستند. ACCOUNT_NOT_FOUND برای حساب درخواستی است؛ نبود تنظیم حساب تأمین داخلی نقص فنی یا تنظیمات است. BALANCE_OVERFLOW مربوط به projection نهایی است؛ عدد بیرون از long در JSON ورودی نامعتبر محسوب می‌شود. دلیل‌های دیگر باید مرور شوند؛ نباید همهٔ exceptionهای دامنه را رد کسب‌وکار دانست.

این شیء هویت درخواستی را نگه می‌دارد، نه reference به entity حساب. در آینده رد حساب غایب باید بدون foreign key به همان حساب ذخیره شود. retry همان شناسه و دستور باید رد اصلی را حتی پس از ایجاد حساب یا افزایش موجودی برگرداند. این نوع‌ها ذخیره‌سازی یا replay را پیاده نمی‌کنند.

## مثال تأیید موفقیت

```java
OperationOutcome outcome = new OperationSuccess(
        new TransactionId("transfer-001"),
        OperationType.TRANSFER
);
```

این مقدار فقط metadata داخلی نتیجه است. map موجودی، ارز، شناسهٔ journal، وضعیت HTTP، پاسخ serializeشده یا برنامهٔ posting ندارد. سازنده نباید این مقادیر را بسازد یا حدس بزند. DTO و خطای transport در CBS23 تعریف می‌شوند؛ مسیر اجرای ماندگار باید پاسخ اصلی serializeشده و metadata دستور canonical، از جمله ارز، را نگه دارد. اگر پاسخ بعداً موجودی داشت، replay باید همان مقدار اصلی را بازگرداند و موجودی فعلی را نخواند. OperationOutcome به‌تنهایی کل پوشش ذخیره‌شدهٔ replay در HTTP نیست.

## ارتباط با TransactionTemplate

ترتیب زیر برنامهٔ پذیرفته‌شدهٔ اتصال است؛ coordinator هنوز پیاده نشده است:

1. دستور را parse و اعتبارسنجی کنید و هویت ماندگار موجود را پیدا کنید. دستور متفاوت conflict می‌گیرد و نتیجهٔ قبلی را جایگزین نمی‌کند. درخواست تکمیل‌شدهٔ منطبق پاسخ اصلی را replay می‌کند.
2. برای عملیات تازه، سیاست پذیرش را بررسی کنید، تراکنش محافظت‌شده را شروع و شناسه را رزرو کنید.
3. حساب‌ها را پیدا و قفل کنید و قواعد کسب‌وکار را بسنجید.
4. در رد کسب‌وکار، فقط عملیات و پاسخ نهایی را ذخیره کنید و Rejected را عادی از callback برگردانید. بازگشت عادی اجازه می‌دهد رد commit شود.
5. در موفقیت، journal، postingها، projectionها و عملیات و پاسخ را با هم ذخیره و Success را از callback برگردانید.
6. فقط پس از پایان موفق template و با نبود تراکنش بیرونی، نتیجه را منتشر یا نگاشت کنید. مقدار برگشتی callback به‌تنهایی شاهد commit نیست.

exception فنی باید از callback خارج شود تا تلاش rollback شود. برای صرفاً نمایش خطای HTTP نباید رد کسب‌وکار را throw کنیم؛ ممکن است رکورد ردی را که باید حفظ شود rollback کنیم. نوع نهایی TechnicalFailure، Timeout یا Deadlock نداریم. rollback موقت ممکن است retry محدود کل تلاش را لازم کند؛ commit نامعلوم به recovery با همان شناسه نیاز دارد. هیچ‌کدام اجازه نمی‌دهد رد commit‌شده دوباره ارزیابی کسب‌وکار شود.

خطای اعتبارسنجی، پذیرش‌نشدن طبق سیاست فعلی ارز، خطای مجوز و conflict شناسه هم خارج از این نتیجه‌های نهایی ذخیره‌شده‌اند. نگاشت HTTP و زمان تراکنش آن‌ها مرز بررسی‌شدهٔ خود را دارد؛ enum آن‌ها را به رکورد commit‌شده تبدیل نمی‌کند.

## وضعیت پیاده‌سازی و شواهد

[OperationOutcome.java](../../src/main/java/dev/kavrin/balance/operation/domain/OperationOutcome.java)، [OperationType.java](../../src/main/java/dev/kavrin/balance/operation/domain/OperationType.java) و [RejectionReason.java](../../src/main/java/dev/kavrin/balance/operation/domain/RejectionReason.java) پیاده شده‌اند. evaluator، coordinator با TransactionTemplate، طبقه‌بندی exception، serialize در repository یا adapter HTTP متصل نیست.

Maven verify با Java 21.0.11 در ۷٫۶۹۶ ثانیه، ۱۵۰ تست را بدون failure، error یا skip گذراند؛ سیزده مورد [نتیجه](../../src/test/java/dev/kavrin/balance/operation/domain/OperationOutcomeTests.java) شکل مقدار، metadata لازم، تمایز دلیل‌ها و رسیدگی کامل را می‌سنجند. این تست‌ها شاهد commit، rollback، رزرو یا replay نیستند. پذیرش CBS05 و تسلط مستقل باز است. گام بعدی پایهٔ DTO و قرارداد خطای HTTP در CBS23 است؛ اجرای واقعی مالی در برش‌های SQL Server می‌آید.

## به‌روزرسانی مرز CBS23 در ۲۰۲۶-۱۰-۲۳

adapter HTTP و پوشش نتیجه/ارز `MutationResult` اکنون وجود دارند. [قرارداد API](API-v1.md) جزئیات را دارد. مثال‌های بالا همچنان فقط ساخت مقدارند. ذخیرهٔ پاسخ ماندگار، coordinator تراکنش، طبقه‌بندی خرابی SQL و replay واقعی پیاده نشده‌اند. نتیجهٔ ۱۵۰ تست بالا سابقهٔ CBS05 است؛ [شواهد فعلی adapter](HTTP-adapter-evidence.md) جدا ثبت می‌شوند.
