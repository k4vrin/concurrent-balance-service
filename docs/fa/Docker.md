# زیرساخت محلی با Docker Compose

[English](../en/Docker.md) · [فهرست](../../README.fa.md)

وضعیت در ۲۰۲۶-۱۰-۲۲: Docker Compose مسیر منتخب زیرساخت محلی است، اما هنوز Compose file یا دیتابیس آماده نداریم. این سند کارهای مسیر آموزشی را تعریف می‌کند و گزارش deploy موفق نیست.

## سرویس‌ها به‌ترتیب مرحله

| کار | آماده‌سازی کانتینر |
| --- | --- |
| CBS00 | بررسی Docker؛ تست bootstrap همچنان فقط Java 21 و Maven wrapper می‌خواهد |
| CBS07 | افزودن Compose برای SQL Server Developer، دادهٔ ماندگار و دیتابیس‌های جداگانهٔ توسعه و تست |
| CBS30–31 | افزودن Redis هنگام پیاده‌سازی بازگرداندن پاسخ نهایی ذخیره‌شده |
| CBS33–35 | افزودن Kafka هنگام پیاده‌سازی publisher و consumer |
| CBS36–37 | بررسی Kafka Connect و Debezium پس از تصمیم سازگاری CDC |

برنامه در شروع با Maven یا IDE روی host اجرا می‌شود و به پورت‌های loopback وصل می‌شود. containerize کردن خود برنامه می‌تواند بعد از آماده‌شدن dependency‌ها انجام شود. وجود کانتینرها به‌معنی فعال‌کردن همهٔ profile‌های Maven و Spring نیست.

## ابتدا میزبان را بررسی کنید

```sh
docker version
docker compose version
```

خروجی باید دسترسی به هر دو جزء Docker client و daemon را نشان دهد. در بررسی ۲۰۲۶-۱۰-۲۲ روی Mac فعلی ARM64، client شناسایی شد، اما daemon در دسترس نبود. کانتینری اجرا نشده و اتصال SQL بررسی نشده است.

### محدودیت ARM64

مایکروسافت SQL Server Linux container را روی Linux host با پردازندهٔ Intel یا AMD با معماری x86-64 پشتیبانی می‌کند. emulation روی ARM64 پیکربندی پشتیبانی‌شدهٔ SQL Server نیست.

برای مثال، `linux/amd64` فقط معماری image را انتخاب می‌کند. این گزینه ثابت نمی‌کند که SQL Server روی Mac فعلی درست اجرا می‌شود.

در CBS07 مسیر emulation محلی، در صورت امکان، تست و محدودیت‌هایش ثبت می‌شود. اگر اجرا نشد یا محیط پشتیبانی‌شده لازم بود، از Docker host با معماری x86-64 استفاده کنید. دیتابیس دیگری جایگزین نکنید و شواهد آن را معادل SQL Server ندانید.

## خروجی مورد انتظار CBS07

### تنظیم Compose

Compose file باید این موارد را داشته باشد:

- image با نسخهٔ ثابت و edition نوع Developer؛
- پذیرش صریح license و رمز محلی خارج از Git؛
- volume نام‌دار برای دادهٔ ماندگار؛
- پورت منتشرشده فقط روی loopback؛
- readiness check.

image digest واقعی، نسخه و edition سرور، compatibility level دیتابیس و isolation را ثبت کنید. پیش‌نیازهای image و حافظهٔ لازم را پیش از اجرا بررسی کنید.

### ساخت دیتابیس و مدیریت volume

دیتابیس‌های خالی و جدا برای توسعه و تست و login اختصاصی برنامه بسازید. `sa` برای مدیریت اولیه می‌ماند. مجوزهای migration و runtime باید پیش از migration کسب‌وکار مرور شوند. رمز واقعی در Compose یا SQL تحت رهگیری Git نباشد.

فرمان‌های start، readiness، logs و stop را مستند کنید. reset مخرب volume باید برچسب جدا داشته باشد. initialization باید restart با volume موجود را تحمل کند؛ برای خطای عادی اتصال، volume را حذف نکنید.

### اتصال برنامهٔ host و container

Spring Boot فایل `.env` مربوط به Compose را خودکار به محیط برنامهٔ host وارد نمی‌کند. `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` را جدا export یا در IDE تنظیم کنید.

| محل اجرای برنامه | آدرس اتصال |
| --- | --- |
| host | localhost و پورت منتشرشده |
| container | نام سرویس Compose و پورت داخلی |

اعتماد به certificate توسعه را صریح بررسی کنید.

### معیار تست CBS08

زیرساخت JDBC integration test واقعی در CBS08 اضافه می‌شود و باید در نبود دیتابیس fail شود. بالا آمدن container یا موفقیت readiness، اتصال برنامه، migration، صحت تراکنش یا CDC را ثابت نمی‌کند. CBS00 تا ثبت تلاش واقعی یادگیرنده باز می‌ماند.

نصب بومی Windows یک [جایگزین اختیاری](Windows-sqlserver.md) است.

منابع: [استقرار و پشتیبانی معماری مایکروسافت](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment?view=sql-server-ver17)، [متغیرهای محیطی Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/).

## دامنهٔ پذیرفته‌شدهٔ گزینهٔ E در ۲۰۲۶-۱۰-۲۲

بعد از مرور CBS02–04، در CBS09 schema چهارجدولی [دفترکل](Ledger.md) اعمال می‌شود. دیتابیس پیش از migration باید خالی باشد. fixture حساب را با projection صفر می‌سازد و افتتاحیهٔ غیرصفر از عملیات داخلی و posting متوازن می‌گذرد.

seed موجودی با `UPDATE` بدون journal مجاز نیست. Docker صحت دفترکل را ثابت نمی‌کند و Compose و migration مالی هنوز پیاده نشده‌اند.
