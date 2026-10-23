# ADR-009: جداسازی مدل command و query در یک دیتابیس

وضعیت: طراحی پذیرفته‌شده توسط مالک پروژه در ۲۰۲۶-۱۰-۲۳. [English](ADR-009-single-database-cqrs.en.md) · [معماری](../fa/Architecture.md)

زمینه: تغییر پول به اعتبارسنجی، قفل، حسابداری و نتیجهٔ ذخیره‌شده نیاز دارد؛ خواندن موجودی یک نمای کوچک commitشده می‌خواهد. الزام مدل دامنهٔ نوشتن برای پاسخ HTTP این دو نیاز را به هم گره می‌زند.

تصمیم: CQRS داخل مونولیت پذیرفته‌شده با مدل و handler جدا برای command/query و یک SQL Server مشترک اجرا شود. commandهای credit/debit/transfer از OperationCoordinator و مسیر مالی تک‌تراکنشی عبور کنند. GetBalanceHandler مستقیماً projection یا BalanceResponse را از دادهٔ commitشدهٔ حساب برگرداند و Account قابل‌نوشتن یا journal نسازد. query تاریخچهٔ آینده DTO خودش را دارد. نوع‌ها در feature خود بمانند؛ لایهٔ سراسری command/query یا command bus اجباری نداریم.

سازگاری: projection حساب همراه نتیجهٔ موفق، سند و posting در یک تراکنش هم‌زمان تغییر می‌کند. query موجودی دیتابیس مرجع commitشده را می‌خواند، نه Redis یا تاریخچهٔ عقب‌ماندهٔ Kafka. یک خواندن موجودی وعدهٔ snapshot مشترک چند query نیست. replay پاسخ اصلی عملیات است، نه query تازه. command می‌تواند نتیجه و موجودی برگرداند؛ پاسخ void اجباری نیست.

گزینه‌ها: مدل مشترک برای CRUD ساده کم‌هزینه‌تر است، ولی اینجا اشیای نوشتن مالی را بی‌نیاز درگیر خواندن می‌کند. دیتابیس خواندن جدا، هماهنگی، تأخیر، بازسازی و deployment بیشتری می‌خواهد و نیاز اندازه‌گیری‌شده نداریم. event sourcing انتخاب ذخیره‌سازی مستقل است. Redis، Kafka و گزارش در مرحلهٔ بعد می‌مانند.

هزینه: DTO/projection و نگاشت بیشتر و تست مرز. بهبود throughput ادعا نمی‌شود. پیش از دسترسی خارجی، مجوز query و mutation لازم است. جزئیات persistence و تراکنش همچنان مرور و تست خود را می‌خواهند.

شاهد لازم هنگام پیاده‌سازی: تست command قواعد مالی را حفظ کند؛ query فقط فیلد commitشدهٔ لازم را بدون تغییر عملیات/سند/projection برگرداند؛ قرارداد HTTP هر دو روشن باشد؛ SQL Server واقعی visibility، rollback و replay را تست کند. بودجهٔ یک query یا خواندن بدون قفل را بدون شاهد اجرا ادعا نکنیم.

migration و بازگشت: جدول یا dependency تازه لازم نیست. اگر جداسازی سود نداشت، مدل application با حفظ قرارداد عمومی، پاسخ عملیات ذخیره‌شده و invariant مالی قابل‌ادغام است. این ADR کد اضافه نمی‌کند.

منابع مرورشده در ۲۰۲۶-۱۰-۲۳: [CQRS مایکروسافت](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs)، [Fowler](https://martinfowler.com/bliki/CQRS.html). توضیح منبع راهنما است، نه اثبات رفتار پروژه.
