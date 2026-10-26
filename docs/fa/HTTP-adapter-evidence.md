> به‌روزرسانی اتصال، ۲۰۲۶-۱۰-۲۶: کنترلر اکنون handlerهای دریافت‌شده در سازنده را مستقیم صدا می‌زند. interface واسط، پیاده‌سازی آن و bean مرحله‌ای حذف شدند. mutation غیرفعال و smoke همان پاسخ 503 را دارند؛ استعلام موجودی عادی در دسترس می‌ماند. توضیحات قدیمی façade در ادامه تاریخی‌اند.

# شواهد adapter HTTP در CBS23

[English](../en/HTTP-adapter-evidence.md) · [قرارداد](API-v1.md) · [نقشهٔ راه](Roadmap.md)

این گام در ۲۰۲۶-۱۰-۲۳ با کمک دستیار پیاده شد. چهار مسیر به مرز تایپ‌شدهٔ `BalanceOperations` متصل‌اند؛ `BalanceService` اولیه همچنان اسکلت bootstrap است. مرز پیش‌فرض خطای صریح عدم دسترسی می‌دهد. این گام عملیات مالی موفق، رزرو هویت، schema یا رفتار SQL Server را پیاده نمی‌کند.

اجرای `./mvnw verify` با Java 21.0.11 در ۹٫۷۷۲ ثانیه به BUILD SUCCESS رسید: ۲۰۰ تست، بدون failure، error یا skip. این اجرا شامل ۴۹ مورد قرارداد HTTP و یک تست جدید با سرور واقعی برای چهار مسیر فاقد handler است. ۱۵۰ تست قبلی هم در همین اجرا حضور دارند. profile تست `smoke`، persistence را حذف می‌کند؛ SQL Server در این اجرا استفاده نشده است.

برای بازتولید، `JAVA_HOME` را روی Java 21 بگذارید:

```sh
./mvnw -Dtest=BalanceControllerTests test
./mvnw verify
```

`BalanceControllerTests` از MockMvc و مرز برنامهٔ mock‌شده استفاده می‌کند. جهت انتقال، هویت دقیق حساس به بزرگی حروف، واحد مستقیم ریال، بازهٔ long، نوع سخت‌گیرانهٔ scalar، فیلد اجباری، UUID کامل، انتقال به خود، فیلد ناشناخته، کلید تکراری، JSON اضافی، شکل DTO موفقیت/استعلام، رد کسب‌وکار، conflict، پذیرش ارز، عدم دسترسی و پنهان‌ماندن خطای داخلی بررسی می‌شوند. خطای روش و media هم Problem پایدار دارد. تکرار نتیجهٔ یکسان فقط serialize قطعی را می‌سنجد؛ هر دو فراخوانی به mock می‌رسند و dedup ماندگار اثبات نمی‌شود.

`ConcurrentBalanceServiceApplicationTests` سرور HTTP واقعی را بدون persistence اجرا می‌کند. درخواست معتبر credit، debit، transfer و balance همگی 503 با `OPERATION_UNAVAILABLE` می‌گیرند. بنابراین fallback موفقیت مالی اعلام نمی‌کند. اجرای عادی برنامه همچنان به SQL Server تنظیم‌شده نیاز دارد؛ `smoke` فقط برای تست است.

اجرای متمرکز اول نشان داد advice محدود به controller، خطای 415 پیش از انتخاب controller را پوشش نمی‌دهد و body خالی می‌ماند. advice سراسری این مرز را اصلاح کرد و اجرای کامل بعدی موفق شد. خطای lock کش Maven در sandbox پیش از compile هم شاهد اجرای تست نبود.

بارگذاری سیاست ارز، طبقه‌بندی خرابی SQL، مرز commit، ذخیرهٔ پاسخ، replay/conflict ماندگار، انتخاب حساب تأمین، نوشتن موجودی و journal و رقابت یا recovery واقعی SQL Server هنوز بررسی نشده‌اند. فرض اپراتور مورداعتماد روی loopback پابرجاست؛ JWT، مالکیت و نقش پیش از دسترسی اشتراکی لازم‌اند. سقف تجاری و مرور قید/تراکنش پیش از migration هم باز است. تست‌های نوشته‌شده با دستیار به معنای پذیرش کار، تسلط مستقل یا عبور از G1 نیستند.

گام بعد CBS06 برای مقایسهٔ canonical نسخه‌دار است؛ سپس زیرساخت و harness در CBS07–08 و بستن CBS02–04 پیش از migration در CBS09. در CBS10–20 باید fallback با مسیر ماندگار HTTP تا SQL Server جایگزین شود و رفتار حسابداری و recovery اثبات شود.

## تکمیل پاسخ بسته در ۲۰۲۶-۱۰-۲۳

controllerهای mutation و mapper اکنون `ResponseEntity<OperationResponseBody>` برمی‌گردانند. این body بسته فقط OperationResponse و کلاس final موسوم به OperationProblem، فرزند ProblemDetail، را می‌پذیرد. رد کسب‌وکار همچنان مقدار بازگشتی است. assertionهای HTTP، content type خطا، status/title/instance و extensionهای سطح اول را بررسی می‌کنند. تست جدید serialize مستقیم نشان می‌دهد JSON کلاس سفارشی با ProblemDetail اصلی یکسان است. serializer فعلی نوع پیش‌فرض about:blank را حذف می‌کند؛ مثال‌های قرارداد دوزبانه با همین رفتار اصلاح شدند. اجرای نهایی `./mvnw verify` با Java 21.0.11 در ۷٫۲۱۶ ثانیه ۲۰۱ تست را بدون failure، error یا skip گذراند. وضعیت اجرای ماندگار یا پذیرش تغییری نکرد.
