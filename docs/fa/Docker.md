# زیرساخت محلی با Docker Compose

[English](../en/Docker.md) · [فهرست](../../README.fa.md)

وضعیت در ۲۰۲۶-۱۰-۲۲: Docker Compose مسیر منتخب زیرساخت محلی است. اسکلت فعلی هنوز فایل Compose یا دیتابیس آماده ندارد. این سند کار آماده‌سازی مسیر آموزشی را مشخص می‌کند و گزارش اجرای موفق نیست.

## سرویس‌ها به‌ترتیب مرحله

| کار | آماده‌سازی کانتینر |
| --- | --- |
| CBS00 | بررسی Docker؛ تست bootstrap همچنان فقط Java 21 و Maven wrapper می‌خواهد |
| CBS07 | افزودن Compose برای SQL Server Developer، دادهٔ ماندگار و دیتابیس‌های جداگانهٔ توسعه و تست |
| CBS30–31 | افزودن Redis هنگام پیاده‌سازی replay پاسخ نهایی |
| CBS33–35 | افزودن Kafka هنگام پیاده‌سازی publisher و consumer |
| CBS36–37 | بررسی Kafka Connect و Debezium پس از تصمیم سازگاری CDC |

در ابتدا برنامه با Maven یا IDE روی میزبان اجرا و به پورت‌های loopback متصل می‌شود. کانتینری‌کردن برنامه می‌تواند پس از آماده‌شدن وابستگی‌ها انجام شود. استفاده از Docker به معنی فعال‌کردن هم‌زمان همهٔ profileهای Maven و Spring نیست.

## ابتدا میزبان را بررسی کنید

```sh
docker version
docker compose version
```

client و daemon هر دو باید در دسترس باشند. در Mac فعلی با معماری ARM64، در ۲۰۲۶-۱۰-۲۲ client شناسایی شد ولی daemon در دسترس نبود. اجرای کانتینر یا اتصال SQL بررسی نشده است.

مایکروسافت کانتینر Linux مربوط به SQL Server را روی میزبان Linux با پردازندهٔ Intel/AMD x86-64 پشتیبانی می‌کند. شبیه‌سازی روی ARM64 پیکربندی پشتیبانی‌شدهٔ SQL Server نیست. گزینهٔ `linux/amd64` فقط معماری image را انتخاب می‌کند و اثبات سازگاری یا اجرای موفق نیست. در CBS07 مسیر توسعهٔ محلی با شبیه‌سازی، در صورت امکان، آزمایش و محدودیت‌هایش ثبت شود. اگر اجرا نشد یا محیط پشتیبانی‌شده لازم بود، از میزبان Docker با معماری x86-64 استفاده شود. دیتابیس دیگری جای SQL Server گذاشته نشود تا شواهد معادل ادعا شود.

## خروجی مورد انتظار CBS07

فایل Compose قابل‌بازبینی با نسخهٔ ثابت image، edition نوع Developer، پذیرش صریح مجوز، رمز محلی خارج Git، volume نام‌دار، پورت منتشرشده فقط روی loopback و readiness check ساخته شود. digest واقعی image، نسخه و edition سرور، compatibility level دیتابیس و تنظیمات isolation ثبت شوند. پیش‌نیازها و حافظهٔ لازم پیش از اجرا بررسی شوند.

دیتابیس‌های خالی و جداگانهٔ توسعه و تست و login اختصاصی برنامه ساخته شوند. `sa` برای مدیریت اولیه بماند؛ مجوز migration و runtime پیش از migration کسب‌وکار بررسی شود. هیچ رمز واقعی در Compose یا SQL رهگیری‌شده نباشد. فرمان‌های شروع، readiness، log، توقف و reset مخرب volume با برچسب جدا مستند شوند. آماده‌سازی باید restart با volume موجود را تحمل کند؛ برای خطای اتصال معمولی volume حذف نشود.

Spring Boot فایل `.env` مربوط به Compose را خودکار وارد محیط برنامهٔ میزبان نمی‌کند. `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` جدا export یا در IDE تنظیم شوند. برنامهٔ میزبان از localhost و پورت منتشرشده و برنامهٔ کانتینری از نام سرویس Compose و پورت داخلی استفاده می‌کند. اعتماد به certificate توسعه صریح بررسی شود.

در CBS08 زیرساخت تست JDBC واقعی اضافه و شکست در نبود دیتابیس ثابت می‌شود. اجرای کانتینر یا readiness به‌تنهایی اتصال برنامه، migration، صحت تراکنش یا پشتیبانی CDC را ثابت نمی‌کند. CBS00 تا ثبت تلاش واقعی یادگیرنده باز می‌ماند.

نصب بومی Windows یک [جایگزین اختیاری](Windows-sqlserver.md) است.

منابع: [استقرار و پشتیبانی معماری مایکروسافت](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment?view=sql-server-ver17)، [متغیرهای محیطی Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/).
