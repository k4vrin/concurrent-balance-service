> به‌روزرسانی bootstrap در ۲۰ اکتبر ۲۰۲۶: SQL Server جای هدف پیشنهادی قبلی Oracle را گرفته است. P0 شروع شده و کامل نیست. dependency و تست health اثبات صحت مالی نیستند.

# گردش‌کار Git و مستندات دوزبانه

وضعیت: گردش‌کار این مخزن. حفاظت شاخه و tagهای مرحله‌ای برنامه‌ریزی شده‌اند؛ این سند فعال بودن آن‌ها را ثابت نمی‌کند. [English](../en/Workflow.md)

## مدل شاخه و انتشار

هدف، `main` محافظت‌شده و همیشه قابل-build همراه شاخهٔ کوتاه و PR است. حفاظت پس از ایجاد check اولیهٔ CI فعال و بررسی شود؛ تا آن زمان وضعیت آن در انتظار است. برای یک توسعه‌دهنده و مراحل مستقل، `develop` دائمی فایدهٔ کافی ندارد و پیشنهاد نمی‌شود. این گردش‌کار سبک و مشخص است، نه مدل تاریخی GitFlow با شاخه‌های develop/release.

نام شاخه lowercase و توصیفی با پیشوند `feature/`، `fix/`، `refactor/`، `docs/`، `test/`، `perf/`، `build/`، `ci/` یا `chore/` باشد. نام دستیار، ابزار یا هویت در شاخه نیاید. شاخهٔ مستقل از `main` به‌روز شروع شود؛ PR وابسته، base و وابستگی را روشن کند. Redis، OTP و Kafka یک PR مشترک نباشند.

tagهای پیشنهادی: `v0.1.0` بعد از خط مبنای HTTP API چهار عملیات در P1/P2، `v0.2.0` برای احراز هویت و دسترسی، `v0.3.0` برای تأیید، `v0.4.0` برای Redis، `v0.5.0` برای Kafka و `v0.6.0` فقط پس از موفقیت CDC. این‌ها برچسب برنامه‌اند و منتشر نشده‌اند. رد امکان‌سنجی CDC مانع حفظ نسخهٔ سالم polling نیست.

پیش‌فرض merge از نوع squash با پیام سه‌بخشی نهایی باشد. تاریخچهٔ مشترک `main` بازنویسی نشود. تغییر معیوب برنامه با PR جدید revert شود. برای دیتابیس، اصلاح روبه‌جلو و migration سازگار expand/contract ترجیح دارد؛ Git revert دادهٔ مالی commitشده را برنمی‌گرداند و معادل بازگشت امن هر migration نیست.

## قالب commit

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

نوع با حرف بزرگ انتخاب شود: `Feat`، `Fix`، `Refactor`، `Docs`، `Test`، `Perf`، `Build`، `CI` یا `Chore`. header امری، توضیح علت در ۱ تا ۳ جمله و جزئیات با bullet واقعی باشد. commitهای کاری و پیام squash هر دو همین ساختار را داشته باشند.

## معیار PR

هر PR مشکل و رفتار نهایی، دامنه، ADR/مرحله، تغییر schema/API، شواهد اعتبارسنجی، محدودیت و اثر rollback را بیان کند. تست معنادار رفتار مالی و خطا لازم است. در کار انفرادی approval خیالی نسازیم و self-review صادقانه ثبت شود.

CI مرحله‌ای است: build/test با Java 21 و wrapper در P0؛ قرارداد HTTP، اعتبارسنجی/خطا و migration و integration SQL Server در P1؛ امنیت/تأیید در P3/P4؛ خرابی Redis در P5؛ Kafka و خرابی consumer در P6. imageها pin و پیش‌نیازها روشن شوند. اعتبارسنجی کامل در نهایت یک فرمان مستند داشته باشد که وابستگی موقت را آماده کند یا فرمان پیش‌نیاز روشن داشته باشد. فرمان unit سریع از integration کامل جدا باشد؛ سبزبودن unit صحت persistence را تأیید نمی‌کند.

قبل از merge، CI لازم سبز، تست invariant موفق، artifact بدون راز/OTP و دو زبان به‌روز باشند. PR کارایی baseline و after مشابه، پلن واقعی لازم و تست رگرسیون صحت داشته باشد. PR صرفاً مستنداتی به بررسی لینک/محتوا نیاز دارد و نباید زیرساخت بی‌دلیل روشن کند.

## مرجع مستندات

اکنون اسناد مخزن مرجع برنامهٔ اجرا و رفتار پیاده‌سازی هستند. فهرست vault به این مخزن و شواهد ارجاع می‌دهد و سابقهٔ بحث را حفظ می‌کند؛ دو specification مستقل ایجاد نشود. PDF اصلی مرجع نیاز شرکت می‌ماند.

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

شناسهٔ ADR، مرحله، query، برچسب وضعیت و identifier کد در دو زبان ثابت باشد. هر دو نسخه در همان PR به‌روز شوند؛ فقط عنوان‌ها ترجمه نشوند. معیار پذیرش و محدودیت‌ها یکسان باشند. block کد/SQL چپ‌به‌راست و توضیح فارسی بیرون آن قرار گیرد. دامنهٔ تأییدشدهٔ کاربر از طراحی پیشنهادی، کد پیاده‌شده و نتیجهٔ اندازه‌گیری جدا ثبت شود.

برای هر مرحله commit/PR، تاریخ، تست واقعاً اجراشده، محیط، مسیر نتیجهٔ پاک‌سازی‌شده، نقص و معیار بعدی ثبت شود. طراحی، گزارش قدیمی یا compile موفق به معنی تکمیل نیست. credential، OTP واقعی، دادهٔ production و PDF شرکت وارد Git عمومی نشوند.

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

تغییر رفتار مالی باید [مرز دفترکل](Ledger.md) را حفظ کند. قید چهار جدول و پروتکل تراکنش پیش از migration مرور شوند. مرور P1 یک سند برای موفقیت، نبود سند برای رد، نبود posting اضافه در replay، تاریخچهٔ تغییرناپذیر و projection اتمیک می‌خواهد. معیار هسته شامل تطبیق مستقل و شاهد واقعی rollback/رقابت SQL Server است. consumer گزارش P6 دوباره پول ثبت نمی‌کند. ترتیب هدایت‌شده و تفکیک طراحی، پیاده‌سازی و یادگیری حفظ شود.
