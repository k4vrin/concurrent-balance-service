# وضعیت پیاده‌سازی

README عمومی قرارداد نسخهٔ اول را شرح می‌دهد. نسخهٔ فعلی قواعد دامنه و پایهٔ HTTP، schema چهار جدول تأییدشده، نگاشت JPA و route واقعی موجودی از SQL Server را دارد. GET موجودی اکنون 200/404 می‌دهد و unavailable موقت query به 503 نگاشت می‌شود. credit/debit/transfer عمومی هنوز unavailable هستند.

آخرین اجرای کامل ۲۴۰ تست مستقل از زیرساخت و ۱۰۳ تست واقعی SQL Server را بدون failure، error یا skip گذراند. [شواهد persistence و افتتاحیه](Persistence.md) افتتاحیهٔ journal-backed committed فقط در تست، طول عمر قفل بومی JPA، سه مرز rollback، reconciliation و HTTP واقعی را شرح می‌دهد. راه‌اندازی در [schema و راهنما](Schema.md) است.

گام بعد slice ماندگار credit/coordinator/replay است. افتتاحیه فقط fixture تست است و route عمومی پنجم نیست. runtime هنوز handler عمومی جابه‌جایی پول، مجوز production یا موتور کامل retry/recovery ندارد.
