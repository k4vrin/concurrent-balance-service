> به‌روزرسانی CBS15، ۲۰۲۶-۱۰-۲۹: mutationها اکنون [retry محدود کل تراکنش و بازیابی محافظه‌کارانهٔ نتیجهٔ نامعلوم](Retry-execution.md) دارند. عبارت‌های پایین دربارهٔ نبود retry مربوط به checkpoint قبلی‌اند. `execute` همچنان یک تراکنش اجرا می‌کند؛ handlerهای mutation از `executeRecovering` استفاده می‌کنند.

# اجرای ماندگار debit و شواهد CBS13

[English](../en/Debit-execution.md) · [اجرای credit](Credit-execution.md) · [مالکیت تراکنش](Operation-coordinator.md) · [API](API-v1.md) · [معماری hexagonal](Hexagonal-architecture.md)

## دامنه و فعال‌سازی

credit مربوط به CBS11–12 و debit مربوط به CBS13 پیاده و regression-test شده‌اند. انتقال اتمیک CBS14 اکنون در slice جداگانه پیاده شده است؛ [اجرای انتقال](Transfer-execution.md) را ببینید. پذیرش رسمی یادگیری از این شواهد پیاده‌سازی جداست.

هر دو عملیات تأمین‌شده به `balance.execution.enabled=true` و سیاست معتبر با حساب تأمین برای هر ارز پشتیبانی‌شده نیاز دارند. همان identity محدود runtime و schema بدون تغییر V1 استفاده می‌شود. [تنظیم سیاست](Execution-policy.md) و [راه‌اندازی SQL Server](Schema.md) را ببینید. در اجرای غیرفعال پیش‌فرض و assembly مخصوص smoke، mutationها همچنان 503 برمی‌گردانند.

## درخواست و جریان حسابداری

مشتری فعال C موجودی ۱۰۰ IRR و حساب تأمین فعال F با ماهیت debit-normal هم موجودی ۱۰۰ دارد. این موجودی‌ها پشتوانهٔ تاریخچهٔ متوازن قبلی دارند. درخواست چنین است:

```http
POST /api/v1/accounts/10000000-0000-4000-8000-000000000001/debits
Content-Type: application/json

{"transactionId":"debit-example-001","amountRial":100,"currency":"IRR"}
```

handler پیش از ارزیابی سیاست فعلی، قصد canonical را با عملیات ماندگار مقایسه می‌کند. برای قصد جدید، عملیات PENDING با `operation_type=DEBIT`، `source_account_id=C`، metadata سیاست و تأمین و بایت canonical رزرو می‌شود. درخواست معتبر بالاتر از سقف مبلغ، پیش از قفل حساب‌ها با رد نهایی ماندگار تکمیل می‌شود.

برای مبلغ مجاز، مجموعهٔ کامل مشتری و تأمین با `AccountLockOrder.DESCENDING` قفل و entityهای مدیریت‌شده refresh می‌شوند. پس از کنترل دسته، ارز و وضعیت حساب، وجه از snapshot قفل‌شدهٔ مشتری بررسی می‌شود. برداشت دقیق موجودی مجاز است. برداشت ۱۰۱ از موجودی ۱۰۰، نتیجهٔ `INSUFFICIENT_FUNDS` را بدون journal یا تغییر projection نگه می‌دارد. نبود مشتری درخواستی، `ACCOUNT_NOT_FOUND` ماندگار است؛ نبود یا ناسازگاری حساب تأمین داخلی نقص فنی است و rollback می‌شود.

موفقیت یک DEBIT به مبلغ ۱۰۰ برای مشتری و CREDIT به مبلغ ۱۰۰ برای تأمین می‌سازد. C و F هر دو صفر می‌شوند، چون بدهی مشتری credit-normal و تأمین debit-normal است. همهٔ مانده‌های پیشنهادی پیش از نوشتن history/projection بررسی می‌شوند. عملیات، یک journal، دو posting، هر دو projection و پاسخ اصلی در یک تراکنش commit می‌شوند. `complete()` مستقل commit نمی‌کند؛ coordinator مالک پس از پایان تراکنش نتیجه را برمی‌گرداند.

همان ID و قصد، بایت پاسخ اصلی را بدون برداشت دوباره replay می‌کند. برداشت ردشدهٔ ۱۰۱ ریالی حتی پس از افزایش وجه با credit بعدی، ردشده می‌ماند؛ ID تازه تصمیم تازه می‌خواهد. تغییر مبلغ، حساب یا نوع عملیات با همان ID تعارض است. timeout قفل، عدم دسترسی است و کسری وجه نیست؛ هیچ رزرو committed باقی نمی‌گذارد. recovery با همان transaction ID انجام می‌شود.

## نقشهٔ سورس

| جزء | مسئولیت |
| --- | --- |
| [DebitCommand](../../src/main/java/dev/kavrin/balance/operation/application/command/DebitCommand.java) | شناسهٔ retry، حساب و مبلغ مثبت؛ بدون سیاست یا دسترسی دیتابیس |
| [DebitAttempt](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitAttempt.java) | ID ثابت عملیات و journal، قصد caller، snapshot اجرا و انتخاب تأمین |
| [DebitHandler](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitHandler.java) | replay پیش از سیاست، تصمیم وجه زیر قفل، محاسبهٔ journal و تکمیل موفقیت یا رد ماندگار |
| [DebitStore](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitStore.java) | پورت persistence وابسته به تراکنش؛ همهٔ نوشتن‌ها در attempt مالک |
| [JpaDebitStore](../../src/main/java/dev/kavrin/balance/operation/infrastructure/persistence/JpaDebitStore.java) | SQL نام‌دار، رزرو حساب مبدأ، mapping مربوط به replay، نوشتن تاریخچه و projection و تکمیل |
| [DebitResponseEncoder](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitResponseEncoder.java) | تولید و بررسی پاسخ ماندگار |
| [JsonDebitResponseEncoder](../../src/main/java/dev/kavrin/balance/operation/web/response/JsonDebitResponseEncoder.java) | encoding با Jackson و instance URI مخصوص debit در problem response |
| [HttpAdapterConfiguration](../../src/main/java/dev/kavrin/balance/operation/web/configuration/HttpAdapterConfiguration.java) | اتصال هر mutation به handler فعال آن |

مطابق راهنمای ثبت‌شده، credit و debit فعلاً sliceهای صریح باقی می‌مانند. مالکیت تراکنش، قفل حساب، هویت canonical و نگاشت نتیجهٔ HTTP مشترک‌اند. پس از تثبیت رفتار هر دو، می‌توان مکانیزم‌های یکسان codec/persistence را در refactor متمرکز جدا کرد؛ مسیر debit هیچ‌گاه SQL رزرو مخصوص credit را صدا نمی‌زند.

## شواهد اجرا، ۲۰۲۶-۱۰-۲۵

دستور Java 21 یعنی `./mvnw -o -B -ntp -Psqlserver-it verify` در **۳۱٫۲۳۵ ثانیه**، **۳۵۱ تست مستقل از زیرساخت و ۱۳۴ تست SQL Server** را با صفر failure، error و skip گذراند. مجموع XML تازهٔ Surefire/Failsafe بررسی شد. این تغییر نسبت به checkout قبلی ۲۶ تست مستقل و ۱۴ تست واقعی SQL Server اضافه می‌کند. اجرای کامل، regressionهای credit، schema، permission، قفل، افتتاحیه و projection مربوط به AccountStore را هم پوشش می‌دهد.

- [DebitHandlerTests](../../src/test/java/dev/kavrin/balance/operation/application/debit/DebitHandlerTests.java): هجده حالت mock/lifecycle برای برداشت دقیق، علامت posting، هر دو projection، replay پیش از سیاست غیرفعال، تعارض، رد ماندگار، underflow تأمین، شکست ذخیره‌سازی/encoding/قفل، commit نامطمئن و recovery پس از rollback رزرو تکراری.
- [JsonDebitResponseEncoderTests](../../src/test/java/dev/kavrin/balance/operation/web/response/JsonDebitResponseEncoderTests.java) و [OperationResponsesTests](../../src/test/java/dev/kavrin/balance/operation/web/response/OperationResponsesTests.java): هفت حالت بررسی پاسخ replay و یک تست serialization خطای debit با mapper تنظیم‌شدهٔ Spring.
- [SqlServerDebitIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerDebitIT.java): چهارده حالت برای موفقیت HTTP با برداشت دقیق و استعلام موجودی، replay کسری وجه پس از credit بعدی، تعارض مبلغ/حساب/نوع عملیات، موفقیت و رد پس از بازسازی application و pool، تغییر سیاست و سقف، رد ماندگار حساب غایب، rollback پس از نوشتن journal/projection/outcome همراه retry موفق همان ID، timeout واقعی حساب مسدود و rollback underflow تزریق‌شدهٔ تأمین. query مستقل JDBC تعداد رکوردهای هر ID، بایت پاسخ، توازن مجموع‌ها، قصد حساب مبدأ و projection بازسازی‌شده از ledger را بررسی می‌کند.

fixtureها فقط حساب صفر را با initializer می‌سازند و مشتری را با credit واقعی committed تأمین می‌کنند. تاریخچهٔ مالی موفق در اجرای دوباره حفظ می‌شود. تست underflow فقط snapshot دامنهٔ قفل‌شده را تزریق می‌کند و موجودی نامنطبق با ledger را commit نمی‌کند. migration، حذف volume، پاک‌سازی داده یا گسترش permission لازم نشد.

اولین اجرای مستقل، نگاشت اشتباه سمت posting هنگام اقتباس slice اعتباردهی را پیدا کرد؛ پیش از اجراهای موفق اصلاح شد. کانتینر موجود SQL Server ابتدا با خطاهای 17300/17312 در startup گیر کرد. restart همان کانتینر، بدون تغییر image، volume، سقف ۴ گیگابایت یا تنظیمات دیگر، آن را بازیابی کرد. تلاش زودهنگام integration هنگام recovery با خطای pre-login در setup شکست خورد؛ پس از healthyشدن و آماده‌شدن پورت loopback، اجرای کامل بالا گذشت. آن شکست‌ها تست موفق حساب نشده‌اند. secretها در محیط محلی ignored ماندند.

## مرزهای باقی‌مانده

اجرای SQL Server 2022 CU22 با emulation روی ARM64 همچنان از طرف Microsoft پشتیبانی نمی‌شود و تست محلی آمادگی production را ثابت نمی‌کند. retry/backoff عمومی، recovery جامع commit نامطمئن، gateهای ترکیبی چند instance، authentication/مالکیت باز هستند. خطای checked arithmetic تأمین فعلاً یک شکست فنی/داخلی است و rollback می‌شود؛ تکمیل طبقه‌بندی عمومی خطا در CBS15 ادعا نشده است.

README عمومی همچنان قرارداد نسخهٔ اولِ نهایی را توضیح می‌دهد. رفتار فعلی در [وضعیت پیاده‌سازی](Implementation-status.md) آمده است. برای اجرای دوبارهٔ suite، هر سه identity محدود تست را مطابق [Schema.md](Schema.md) صریح تنظیم کنید؛ secret را در property خط فرمان Maven قرار ندهید.
