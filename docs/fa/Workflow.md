> به‌روزرسانی bootstrap در ۲۰ اکتبر ۲۰۲۶: SQL Server جای هدف پیشنهادی قبلی Oracle را گرفته است. P0 شروع شده و کامل نیست. وجود وابستگی‌ها و موفقیت تست سلامت، صحت مالی را ثابت نمی‌کنند.

# گردش‌کار Git و مستندات دوزبانه

وضعیت: گردش‌کار این مخزن. حفاظت شاخه و تگ‌های مرحله‌ای برنامه‌ریزی شده‌اند؛ این سند فعال‌بودن آن‌ها را تأیید نمی‌کند. [English](../en/Workflow.md)

## مدل شاخه و انتشار

### main و شاخه‌های کاری

هدف، `main` محافظت‌شده و همیشه قابل‌build است. تغییرها در شاخه‌های کوتاه‌عمر و از طریق PR وارد می‌شوند. branch protection را بعد از ایجاد check اولیهٔ CI فعال و بررسی کنید؛ تا آن زمان وضعیت آن باز است.

برای یک توسعه‌دهنده و مراحل مستقل، `develop` دائمی سود کافی ندارد. این گردش‌کار GitFlow تاریخی با شاخه‌های develop و release نیست.

### نام‌گذاری و وابستگی PR‌ها

نام شاخه باید توصیفی، lowercase و با کلمه‌های جداشده با خط تیره باشد. پیشوندهای مجاز:

- `feature/`، `fix/` و `refactor/`؛
- `docs/`، `test/` و `perf/`؛
- `build/`، `ci/` و `chore/`.

نام یا هویت دستیار و ابزار در شاخه نمی‌آید. شاخهٔ مستقل را از `main` به‌روز بسازید؛ PR وابسته باید base و وابستگی‌اش را روشن کند. Redis، OTP و Kafka را در یک PR نریزید.

### release‌های برنامه‌ریزی‌شده

| tag پیشنهادی | معیار |
| --- | --- |
| `v0.1.0` | baseline چهار عملیات HTTP در P1/P2 |
| `v0.2.0` | احراز هویت و مجوز |
| `v0.3.0` | تأیید انتقال |
| `v0.4.0` | Redis |
| `v0.5.0` | Kafka |
| `v0.6.0` | فقط بعد از موفقیت CDC |

این tag‌ها هنوز منتشر نشده‌اند. اگر امکان‌سنجی CDC رد شد، نسخهٔ سالم polling باقی می‌ماند.

### merge و بازگشت تغییر

پیش‌فرض merge از نوع squash با پیام نهایی سه‌بخشی است. تاریخچهٔ مشترک `main` را بازنویسی نکنید؛ تغییر معیوب برنامه را با PR تازه revert کنید.

مثلاً revert کد انتقال، پولِ commit‌شده را برنمی‌گرداند. برای دیتابیس، forward migration سازگار با expand/contract ترجیح دارد. Git revert جای روش امن rollback یک migration را نمی‌گیرد.

## قالب commit

پیام باید دلیل تغییر را برای کسی روشن کند که بعداً تاریخچه را می‌خواند. نمونهٔ سه‌بخشی:

```text
Feat: Add atomic transfer confirmation

Bind approval to an immutable transfer intent so concurrent confirmations
cannot execute the same authorized transfer twice.

Key changes include:

- Add intent persistence and expiry validation.
- Commit code consumption and transfer execution together.
- Add replay and concurrent-confirmation integration tests.
- Update English and Persian authorization documents.
```

نوع با حرف بزرگ شروع می‌شود: `Feat`، `Fix`، `Refactor`، `Docs`، `Test`، `Perf`، `Build`، `CI` یا `Chore`. عنوان امری است، دلیل تغییر در ۱ تا ۳ جمله می‌آید و جزئیات فهرست می‌شوند. commit‌های کاری و پیام نهایی squash هر دو همین قالب را دارند.

## معیار PR

در توضیح PR این موارد را ثبت کنید:

- مشکل و رفتار نهایی؛
- دامنه و ADR یا مرحلهٔ مرتبط؛
- تغییر schema یا API؛
- validation واقعاً انجام‌شده و محدودیت‌ها؛
- اثر rollback.

رفتار مالی و مسیر خطا تست معنادار می‌خواهند. در کار انفرادی، self-review را صادقانه ثبت کنید و approval بازبین دیگری را وانمود نکنید.

### CI مرحله‌ای

| مرحله | بررسی لازم |
| --- | --- |
| P0 | build و تست با Java 21 و wrapper |
| P1 | قرارداد HTTP، validation، خطا، migration و SQL Server integration |
| P3/P4 | امنیت و confirmation |
| P5 | خرابی Redis |
| P6 | Kafka و خرابی consumer |

image‌ها باید pin باشند و پیش‌نیازها روشن بمانند.

validation کامل باید در نهایت یک فرمان مستند داشته باشد که dependency موقت را آماده کند یا فرمان پیش‌نیازش روشن باشد. اجرای سریع unit test از integration کامل جداست. سبزبودن unit test، persistence را تأیید نمی‌کند.

### پیش از merge

- CI الزامی و تست invariant‌ها موفق باشند.
- artifact راز یا OTP نداشته باشد و هر دو زبان به‌روز باشند.
- PR کارایی، baseline و after قابل‌مقایسه، actual plan لازم و regression test صحت داشته باشد.
- برای PR صرفاً مستنداتی، لینک و محتوا را بررسی کنید؛ زیرساخت بی‌دلیل لازم نیست.

## توسعه بدون دیتابیس

برای چرخهٔ روزمرهٔ تست‌های مستقل از زیرساخت، از Java 21 و Maven wrapper استفاده کنید:

```powershell
.\mvnw.cmd verify
```

این فرمان تست‌های دامنه، لایهٔ کاربرد، بخش HTTP و smoke مستقل را بدون SQL Server و Docker اجرا می‌کند. در سیستم‌های دیگر از `./mvnw verify` استفاده کنید. کلاس `CreditHandlerTests`، handler و coordinator واقعی را با یک transaction manager ثبت‌کنندهٔ رویدادهای Spring می‌سازد. وابستگی‌های `CreditStore` و `CreditResponseEncoder` mock هستند، اما محاسبات حساب، journal و projection واقعی‌اند. تبدیل پاسخ به JSON در تست‌های جداگانهٔ encoder و HTTP بررسی می‌شود. این تغییر، نحوهٔ ساخت برنامهٔ اصلی را عوض نمی‌کند و اجرای برنامه بدون دیتابیس را اضافه نمی‌کند.

برای خطای loopback در JDK ویندوز با پیام‌های `Unable to establish loopback connection` و `Invalid argument: connect`، محیط فعلی با گزینهٔ `-DargLine=-Djdk.net.unixdomain.tmpdir=D:/Projects/concurrent-balance-service/target` موفق شد. مسیر مطلق یک پوشه در workspace خودتان را جایگزین کنید. این گزینه به JVM تست مربوط است و تنظیم برنامه نیست. [شواهد اجرای تست‌ها](Implementation-status.md).

ترکیب‌های متنوع validation و تصمیم‌های کسب‌وکار را در تست‌های مستقل از زیرساخت پوشش دهید. رابط‌های لایهٔ کاربرد و ذخیره‌سازی را در مرز وابستگی mock کنید؛ جزئیات داخلی JPA یا قفل دیتابیس را شبیه‌سازی نکنید. تست‌های handler، موفقیت، replay پیش از ارزیابی سیاست تغییریافته، تعارض، رد نهایی، سرریز محاسباتی، خرابی ذخیره‌سازی یا commit و بازیابی پس از رزرو تکراری را پوشش می‌دهند. manager ثبت‌کننده فقط تصمیم‌های چرخهٔ تراکنش Spring را بررسی می‌کند؛ شاهد ذخیرهٔ پایدار یا rollback واقعی نیست. replay در این تست‌ها فرض می‌کند adapter نتیجهٔ نگه‌داری‌شده را برمی‌گرداند؛ تطبیق واقعی هویت و رقابت درخواست‌ها همچنان به SQL Server نیاز دارند.

وقتی محیط تست SQL Server آماده است، مجموعهٔ integration را جداگانه اجرا کنید:

```powershell
.\mvnw.cmd -Psqlserver-it verify
```

پیش‌نیازهای محیط و هویت‌های جداگانه در [Schema.md](Schema.md) آمده‌اند. تست‌های migration، مجوز و constraint، نگاشت persistence، مسیرهای نماینده از HTTP تا دیتابیس، commit اتمیک journal و projection و نتیجهٔ عملیات، rollback و شواهد concurrency و خرابی را در این مجموعه نگه دارید. انتخاب این profile به زیرساخت آن نیاز دارد؛ تست‌های غیرقابل‌اجرا را بی‌صدا skip نکنید. موفقیت تست‌های مستقل، ادامهٔ توسعه را ممکن می‌کند، اما پذیرش رفتار دیتابیس باز می‌ماند. اجرای عادی برنامه همچنان به SQL Server پیکربندی‌شده نیاز دارد. H2 یا fake در حافظه، رفتار SQL Server را ثابت نمی‌کند.

## مرجع مستندات

اسناد مخزن مرجع برنامهٔ اجرا و رفتار پیاده‌شده هستند. index در vault به مخزن و شواهد ارجاع می‌دهد و سابقهٔ بحث را نگه می‌دارد. دو specification مستقل نسازید. PDF اصلی مرجع نیاز شرکت باقی می‌ماند.

ساختار پیشنهادی مخزن:

```text
docs/
  en/architecture.md
  fa/architecture.md
  adr/ADR-001.en.md
  adr/ADR-001.fa.md
  performance/Q02/<run-id>/
  evidence/phase-status.md
README.md
README.fa.md
```

شناسهٔ ADR، مرحله و query، معنای status و identifier‌های کد در دو زبان یکسان می‌مانند. هر دو نسخه در همان PR به‌روز می‌شوند؛ ترجمه فقط عنوان‌ها نیست. معیار پذیرش و محدودیت‌ها هم باید یکسان باشند.

بلوک کد و SQL چپ‌به‌راست و توضیح فارسی بیرون آن می‌ماند. دامنهٔ تأییدشده، طراحی پیشنهادی، کد پیاده‌شده و اندازه‌گیری را جدا ثبت کنید.

برای هر مرحله، commit یا PR، تاریخ، تست واقعاً اجراشده، محیط، مسیر نتیجهٔ پاک‌سازی‌شده، نقص‌ها و معیار بعدی را ثبت کنید. طراحی، گزارش قدیمی یا compile موفق، تکمیل مرحله نیست. credential، OTP واقعی، دادهٔ production و PDF شرکت وارد Git عمومی نمی‌شوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

تغییر مالی باید [مرز دفترکل](Ledger.md) را حفظ کند. constraint‌های چهار جدول و پروتکل تراکنش پیش از migration مرور می‌شوند. پذیرش P1 به این شواهد نیاز دارد:

- یک journal برای موفقیت و هیچ journal برای رد؛
- نبود posting اضافه در replay؛
- تاریخچهٔ immutable و projection اتمیک؛
- reconciliation مستقل و rollback و concurrency واقعی SQL Server.

consumer گزارش در P6 دوباره پول ثبت نمی‌کند. ترتیب هدایت‌شده و تفکیک طراحی، پیاده‌سازی و یادگیری حفظ می‌شوند.

## توضیح کنار مرزهای صحت مالی

کنار کدی که invariant مالی یا concurrency غیرآشکار را حفظ می‌کند، comment کوتاه بگذارید. توضیح باید دلیل این تصمیم‌ها را روشن کند:

- قفل حساب پیش از funds check؛
- lock order ثابت برای تمام حساب‌های درگیر؛
- commit مشترک projection و posting؛
- نبود اثر مالی تازه در replay؛
- حفظ هویت در retry کل تراکنش.

رد نهایی کسب‌وکار و خرابی موقت زیرساخت توضیح جدا می‌خواهند.

comment کنار همان کد می‌ماند و در صورت نیاز به ADR یا invariant پایدار ارجاع می‌دهد. تست شاهد صحت است و comment دلیل تصمیم را توضیح می‌دهد.

فراخوانی بدیهی را شرح ندهید و روی همهٔ متدها comment نگذارید. lock order را تضمین حذف تمام deadlock‌ها معرفی نکنید. با تغییر پروتکل، comment را هم اصلاح کنید؛ این قاعدهٔ مستندسازی، پیاده‌سازی مالی ایجاد نمی‌کند.
