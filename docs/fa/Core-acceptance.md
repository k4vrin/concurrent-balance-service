# تکمیل هسته و پذیرش قابل‌تکرار CBS20

[English](../en/Core-acceptance.md) · [راه‌اندازی schema](Schema.md) · [Docker](Docker.md) · [شواهد بازیابی](Recovery-acceptance.md)

## محدودهٔ تکمیل

هدف CBS20/G1 هستهٔ چهارعملیاتی برای اپراتور مورد اعتماد و loopback است: credit، debit، transfer اتمیک و استعلام موجودی مرجع، همراه با identity ماندگار درخواست، رد نهایی، history متوازن immutable و projection هم‌زمان. پیاده‌سازی هستهٔ منتخب و پذیرش خودکار مهندسی کامل شده‌اند.

پیاده‌سازی مهندسی و پذیرش خودکار با تسلط مستقل یادگیرنده، تأیید کارفرما یا آمادگی production فرق دارند. این مرحله مجوز deploy مشترک/خارجی نیست. احراز هویت/مالکیت، آزمایش‌های کارایی، OTP، Redis، Kafka/outbox، CDC و FX بعد از معیار هستهٔ منتخب، توسعه‌های تعویق‌افتاده‌اند.

## بازتولید از راه‌اندازی

Java 21، Python 3 و Docker لازم‌اند. image در Compose همچنان pin شده است. پیش از startup نخست، مجوز Developer را بخوانید و صریح بپذیرید؛ شبیه‌سازی ARM64 همان آزمایش محلی خارج از پشتیبانی است. تست با engine دیگر این معنا را ثابت نمی‌کند.

1. `.env.example` را به `.env.sqlserver` که ignored است کپی و secretهای جدا برای administrator، runtime، migrator و initializer تنظیم کنید. روی Unix دسترسی فایل را 0600 نگه دارید.
2. محیط را با `docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver` و `docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh` بالا بیاورید و bootstrap کنید.
3. طبق [Schema.md](Schema.md)، identityهای محدود را بسازید، هر دو `cbs_dev` و `cbs_test` را migrate/validate و grantهای بررسی‌شده را اعمال کنید. runtime schema کسب‌وکار را خودکار migrate نمی‌کند.
4. دستور واحد بررسی هسته را اجرا کنید. فایل env اختیاری فقط به‌صورت داده parse می‌شود؛ shell آن را اجرا نمی‌کند.

```bash
python3 scripts/verify-core.py --env-file .env.sqlserver
```

بدون `--env-file`، هر سه گروه URL/username/password از `SQLSERVER_TEST_*`، `SQLSERVER_MIGRATION_TEST_*` و `SQLSERVER_INITIALIZER_TEST_*` را صریح export کنید. اتصال واقعی JDBC پیش از ساخت fixture باید `cbs_test`، Microsoft SQL Server و identity محدود را تأیید کند. fallback به تنظیمات production/runtime نداریم. `--offline` از cache پر Maven استفاده می‌کند؛ `--skip-build` فقط سناریوی artifact را پس از build جداگانهٔ تأییدشده اجرا و این موضوع را در گزارش ثبت می‌کند. این automation روی همین macOS سنجیده شده و CI پیکربندی‌شده Linux است. مسیر Maven/PowerShell مستند Windows باقی است تا این harness جداگانه آنجا هم سنجیده شود.

## دستور واحد چه چیزی را ثابت می‌کند

دستور پیش‌فرض ابتدا Maven روی Java 21 با `-Psqlserver-it verify` را اجرا می‌کند، سپس فقط driver JDBC را از JAR ساخته‌شده بیرون می‌آورد و checker مستقل JDBC را compile می‌کند. سه حساب صفر تازه با initializer محدود ساخته می‌شوند. همهٔ وجه از credit واقعی HTTP برنامهٔ بسته‌بندی‌شده وارد می‌شود، نه seed مستقیم balance.

JAR اجرایی واقعی در JVM جدا با credential محدود runtime، پورت موقت loopback و سیاست funding صریح بالا می‌آید. سناریو این مراحل را دارد:

- creditهای ۱۰۰۰ و ۵۰۰ ریالی، debit صدریالی و transfer دویست‌وپنجاه‌ریالی؛ موجودی مشتری‌ها ۶۵۰/۷۵۰ می‌شود.
- transfer با کسری وجه ۶۵۱ ریالی، سپس شارژ صدریالی؛ رد اصلی با وجود وجه کافی تازه باید replay شود.
- replay موفقیت اصلی، conflict مبلغ متفاوت با همان ID و رد ورودی کسری بدون operation ماندگار.
- قفل واقعی خارجی حساب در SQL. transfer متوقف‌شده 503 می‌دهد؛ JDBC مستقل نبود operation ماندگار را ثابت می‌کند. پس از آزادشدن قفل، retry همان ID یک بار ۲۵ ریال منتقل می‌کند.
- خاموش/روشن‌شدن کامل process برنامه. پاسخ موفقیت، رد و انتقال بازیابی‌شده باید بایت‌به‌بایت replay و موجودی‌ها بدون تغییر بمانند.

checker مستقل JDBC دقیقاً هفت عملیات نهایی می‌خواهد: شش موفقیت، یک رد، بدون PENDING، شش journal متوازن و دوازده posting. هر سه projection را با `decimal(38,0)` از history immutable بازسازی می‌کند. موجودی نهایی مشتری‌ها **۷۲۵/۷۷۵** و projection funding برابر **۱۵۰۰** است. fixtureها scope یکتا دارند و نگه داشته می‌شوند؛ harness تاریخچهٔ مشترک را پاک، credential را rotate، grant را تغییر یا دیتابیس را دوباره ایجاد نمی‌کند.

شواهد تولیدشده در `target/core-acceptance/` که ignored است می‌مانند: log Maven، process نخست/پس از restart و خلاصهٔ JSON با HEAD، وضعیت dirty tree، checksum artifact و نتیجهٔ سنجیده‌شده. پیش از موفقیت، مقادیر دقیق credential در logها scan می‌شوند. محیط برنامهٔ restart‌شده credential migrator/initializer/administrator و overrideهای محیطی Spring/datasource را ندارد.

## ماتریس پذیرش

| معیار | شاهد |
| --- | --- |
| چهار عملیات واقعی HTTP تا SQL | سناریوی ترکیبی process بسته‌بندی‌شده و suiteهای integration. |
| history متوازن و projection/outcome اتمیک | تست schema/permission، rollback مرزهای نوشتن همهٔ عملیات و بازسازی مستقل. |
| موفقیت/رد ماندگار و conflict | raceهای CBS17 بین contextها و restart/replay process مستقل. |
| هم‌زمانی و پیشرفت مستقل | دستورهای متفاوت CBS16 و انتقال معکوس/پیشرفت CBS18. |
| بازیابی خطای موقت و نامعلوم | حالت‌های واقعی 1205/1222، faultهای مرزی کنترل‌شدهٔ CBS19 و rollback/retry واقعی process قفل‌شده. |
| bootstrap/بررسی قابل‌تکرار | راهنمای Docker/schema pin‌شده و دستور واحد بالا. |
| Javadoc همهٔ typeها و ساختار source | پوشش DocTrees، doclint کل پروژه و صفر type تودرتو/local/anonymous. |
| CI | بررسی قابل‌حمل و job تازهٔ SQL Server موقت/پذیرش artifact؛ برای push و pull request تنظیم شده است؛ وضعیت اجرای remote باید برای commit منتشرشده بررسی شود. |
| پذیرش مستقل یادگیری | به اجرای خود یادگیرنده نیاز دارد؛ از پیاده‌سازی کمکی نتیجه نمی‌شود. |

## نتیجهٔ سنجیده‌شده و محدودیت‌ها

اجرای کامل Java 21 و Maven با `-Psqlserver-it verify` در ۲۰۲۶-۱۰-۲۹، **۴۲۱ تست قابل‌حمل + ۲۱۲ تست واقعی SQL Server** را با **صفر failure/error/skip** در **01:05 min** گذراند. جمع XMLهای تازهٔ Surefire/Failsafe بررسی شد.

سپس entry point پیش‌فرض، سناریوی JAR در process جدا را گذراند: **۷ operation نهایی، ۶ موفقیت، ۱ رد، ۶ journal و ۱۲ posting**؛ موجودی مشتری‌ها **725/775** و funding برابر **1500**. restart واقعی process، rollback ناشی از lock timeout واقعی و retry با همان ID گذشتند. SHA-256 artifact اندازه‌گیری‌شده `54c6816d6da294ef477e4a8bfa95c77290e2f95686e20312f65c95870686d730` بود. گزارش، HEAD والد و dirty بودن tree را ثبت می‌کند، چون پذیرش پیش از انتشار تغییرات نهایی تست/مستندات انجام شد.

زمان بالا SLO تولیدی یا benchmark توان عملیاتی نیست. fixtureهای race بین contextها یک JVM دارند؛ faultهای تأیید/visibility/پاسخ در CBS19 صریحاً تزریق می‌شوند و قطع فیزیکی شبکه نیستند. سناریوی مستقل artifact از JVM جدا، timeout واقعی قفل و restart process استفاده می‌کند. این محدودهٔ شواهد جزئی از تحویل هسته است.

ممیزی نهایی کامپایلر، **۱۶۳ type جاوا را با صفر JavaDoc کلاسیِ مفقود** پوشش می‌دهد. تولید JavaDoc کل پروژه، شامل اعضای private، doclint را بدون warning/error گذراند؛ اسکن **۱۶۱ فایل source، صفر type تو‌در‌تو/local/anonymous** یافت.
