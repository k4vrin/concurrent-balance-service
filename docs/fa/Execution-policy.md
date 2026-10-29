> به‌روزرسانی CBS15، ۲۰۲۶-۱۰-۲۹: mutationها اکنون [retry محدود کل تراکنش و بازیابی محافظه‌کارانهٔ نتیجهٔ نامعلوم](Retry-execution.md) دارند. عبارت‌های پایین دربارهٔ نبود retry مربوط به checkpoint قبلی‌اند. `execute` همچنان یک تراکنش اجرا می‌کند؛ handlerهای mutation از `executeRecovering` استفاده می‌کنند.

# snapshot سیاست اجرا و سقف‌های credit

[English](../en/Execution-policy.md) · [تصمیم سقف مبلغ](Amount-limits.md) · [Coordinator](Operation-coordinator.md) · [قرارداد persistence](CBS09-contract.md)

در CBS11 دو نوع application مستقل از framework برای ورودی تلاش مالی و یک adapter اختیاری تنظیمات typed در Spring داریم. value typeها به SQL Server وصل نمی‌شوند، transaction ID رزرو نمی‌کنند و credit اجرا نمی‌کنند. [slice ماندگار credit](Credit-execution.md) اکنون در حالت فعال از این ورودی‌ها استفاده می‌کند؛ [debit](Debit-execution.md) و [transfer](Transfer-execution.md) هم در حالت فعال از snapshot استفاده می‌کنند. انتقال شواهد سیاست اجرا را بدون انتخاب حساب funding نگه می‌دارد. سیاست اولیهٔ پذیرفته‌شده IRR با سقف 10000000000000 ریال برای هر دو محدودیت است؛ نوع عمومی سیاست این انتخاب deployment را hardcode نمی‌کند.

## نگه‌داشتن ورودی‌های سیاست کنار هم

اعتبارسنجی نسخه در utility داخلی پکیج یعنی `IdentityText` قرار دارد و بین نسخهٔ اجرا، نسخهٔ funding و هویت اختیاری actor مشترک است. متن باید موجود، غیرخالی و Unicode معتبر باشد؛ trim یا normalization انجام نمی‌شود. `ExecutionPolicy` دیگر به `CanonicalFingerprint` وابسته نیست. مسئولیت encoding دقیق بایت و hashing همچنان با fingerprint است.

`ExecutionPolicy` شامل نسخهٔ سیاست اجرا، `SupportedCurrencies` تغییرناپذیر، `AmountLimits` و کپی map ارز به `FundingSelection` است. نسخهٔ سیاست اجرا و funding مستقل می‌مانند. هر ارز مجاز دقیقاً یک mapping لازم دارد؛ ورودی اضافی یا کمبود mapping هنگام ساخت به‌عنوان تنظیم نامعتبر رد می‌شود. مجموعهٔ مجاز خالی همراه map خالی، امکان موجودِ غیرفعال‌کردن همهٔ ارزها را حفظ می‌کند.

هنگام ساخت از map کپی گرفته می‌شود و accessor آن تغییرناپذیر است. جایگزینی ورودی map در loader، snapshot قبلاً گرفته‌شده را عوض نمی‌کند. coordinator آینده باید همان snapshot را بین تلاش‌ها نگه دارد و شواهد لازم را ذخیره کند. value object به‌تنهایی retry یا مدیریت نسخهٔ ماندگار اجرا نمی‌کند.

متد `fundingFor(currency)` حساب مقابل انتخاب‌شده توسط سرور و نسخهٔ سیاست funding را برمی‌گرداند. lookup ارز غیرمجاز از بررسی موجودِ ارزهای پشتیبانی‌شده رد می‌شود. ساخت snapshot وجود حساب، ارز درست، status فعال یا دستهٔ FUNDING_CLEARING را ثابت نمی‌کند. این بررسی‌ها داخل تراکنش قفل‌شدهٔ آینده انجام می‌شوند؛ funding داخلی غایب نقص تنظیمات است، نه رد تجاری مشتری. قفل‌ها باید مجموعهٔ کامل و بدون تکرار حساب‌ها را با ترتیب مشترک برنامه پوشش دهند.

## بررسی سقف بدون تغییر قواعد نمایش مبلغ

در `AmountLimits` سقف‌های تنظیم‌شده باید مثبت باشند. دو predicate دارد:

| predicate | شرط پذیرش |
| --- | --- |
| acceptsTransactionAmount(amount) | amount > 0 و amount <= maxTransactionAmount |
| acceptsCustomerBalance(balance) | balance >= 0 و balance <= maxCustomerBalance |

تست مبلغ 1 و دقیقاً 10000000000000 را می‌پذیرد و 10000000000001، Long.MAX_VALUE، صفر و منفی را رد می‌کند. موجودی مشتری صفر یا دقیقاً سقف پذیرفته می‌شود؛ مقدار منفی یا بالاتر از سقف رد می‌شود. سقف‌ها مستقل‌اند، هرچند مقدار اولیهٔ یکسان دارند. transfer مبلغ جابه‌جاشده را یک بار بررسی می‌کند، نه مجموع postingها را.

predicate مشتری روی funding اعمال نمی‌شود؛ funding قواعد signed-long دستهٔ خود را دارد. predicateها فقط مقایسه می‌کنند. محاسبهٔ checked در projection سند باید پیش از هر تغییر پولی موجودی‌های پیشنهادی را آماده کند. این نوع‌ها جای بررسی overflow، موجودی کافی، دسته یا status را نمی‌گیرند.

قواعد نمایش `Money` و اعتبارسنجی مثبت‌بودن command تغییر نکرده‌اند. مبلغ مثبت بالاتر از سقف همچنان برای رد نهایی قابل parse و ذخیره است. نتیجهٔ false یا lookup غیرمجاز در این نوع‌ها، رد نهایی ذخیره نمی‌کند و HTTP status تعیین نمی‌کند. lookup نتیجهٔ قبلی و مقایسهٔ canonical باید پیش از ارزیابی سیاست فعلی انجام شود. replay رد قبلی پس از افزایش سقف همان تصمیم اصلی را برمی‌گرداند. درخواست canonical نیت caller را بیان می‌کند، نه سقف فعلی را.

## شواهد و گام بعد

کامپایل اولیهٔ تست‌ها به‌دلیل نبود نوع‌ها شکست خورد. اجرای نهایی Java 21 با `./mvnw -o -B -ntp verify` در ۸٫۵۲۳ ثانیه، هر ۲۶۶ تست مستقل از زیرساخت را بدون failure، error یا skip گذراند؛ ۱۹ مورد تازه مربوط به سیاست‌اند. مرز دقیق، سقف نامعتبر، سقف‌های مستقل، مالکیت map، snapshot متفاوت، ارز غیرمجاز، غیرفعال‌بودن همهٔ ارزها، نسخهٔ نامعتبر و اجزای غایب بررسی شدند. این‌ها تست value سیاست‌اند، نه شاهد مالی SQL Server؛ اجرای قبلی ۱۰۶ تست دیتابیس پیش از این نوع‌ها بود.

بخش بعد loader بیرونی را که در ادامه پیاده شد توضیح می‌دهد. [پیاده‌سازی بعدی credit](Credit-execution.md) lookup شناسه، ورودی نگه‌داشته‌شده، رزرو، بررسی قفل‌شده، ذخیرهٔ اتمیک، نتیجهٔ نهایی سقف و replay را اضافه کرده است. gate گسترده‌تر retry و عملیات ترکیبی باقی است. این پیاده‌سازی کمکی به‌معنای پذیرش کار یا تسلط یادگیرنده نیست.

## بارگذاری یک snapshot از تنظیمات Spring

کلاس `ExecutionPolicyProperties` تنظیمات `balance.execution` را به ارز، UUID، سقف و نسخهٔ typed تبدیل می‌کند. `ExecutionPolicyConfiguration` هنگام startup با شرط `balance.execution.enabled=true`، یک bean معتبر از `ExecutionPolicy` می‌سازد. پیش‌فرض فعال‌سازی false است؛ اکنون فعال‌کردن آن handler ماندگار credit را هم متصل می‌کند. احراز هویت یا ایجاد حساب انجام نمی‌دهد.

| property | پیش‌فرض یا الزام |
| --- | --- |
| version | execution-v1 |
| supported-currencies | IRR |
| max-transaction-amount | 10000000000000 |
| max-customer-balance | 10000000000000 |
| funding[currency].account-id | UUID لازم برای هر ارز مجاز |
| funding[currency].policy-version | نسخهٔ لازم، غیرخالی و Unicode معتبر |

YAML اصلی برنامه mapping مربوط به IRR را با placeholder محیطی فراهم می‌کند. این متغیرها را کنار تنظیمات عادی دیتابیس runtime صادر کنید:

```bash
export EXECUTION_POLICY_ENABLED=true
export EXECUTION_IRR_FUNDING_ACCOUNT_ID='<UUID of the selected internal funding account>'
export EXECUTION_IRR_FUNDING_POLICY_VERSION='funding-v1'
```

مقدار داخل علامت زاویه‌دار نمونهٔ placeholder است و UUID قابل‌استفاده نیست. loader شناسهٔ حساب اختراع نمی‌کند و حساب نمی‌سازد. `.env.example` فقط مرجع است و Spring خودکار آن را بارگذاری نمی‌کند. overrideهای scalar اختیاری شامل `BALANCE_EXECUTION_VERSION`، `BALANCE_EXECUTION_MAXTRANSACTIONAMOUNT` و `BALANCE_EXECUTION_MAXCUSTOMERBALANCE` هستند. binder از مجموعه و map ارزِ ورودی پشتیبانی می‌کند؛ جایگزینی تنظیم اولیهٔ IRR با مجموعه‌ای دیگر، تنظیم کامل و mapping متناظر می‌خواهد و با تغییر یک متغیر ارز تمام نمی‌شود. رفتار FX اضافه نشده است.

در حالت فعال، UUID یا ارز نامعتبر، سقف نادرست، نسخهٔ نامعتبر و mapping ناقص startup را متوقف می‌کنند. حالت غیرفعال هیچ bean سیاستی نمی‌سازد. assembly مستقل smoke تنظیم سیاست را import نمی‌کند؛ فعال‌سازی سیاست در production شرط profile آزمایشی ندارد. bean یک snapshot زمان startup است و hot reload یا مخزن سیاست دیتابیس ندارد. هنگام انتشار قواعد جدید نسخه را آگاهانه تغییر دهید؛ loader نسخه تولید نمی‌کند و یکتایی عمومی آن را تضمین نمی‌کند. نتیجهٔ ماندگار قبلی همچنان باید پیش از ارزیابی سیاست فعلی replay شود. وجود، دسته، status و ارز واقعی حساب باید داخل handler قفل‌شده بررسی شوند.

چهارده تست context و binding تازه، از جمله placeholderهای واقعی YAML و شکست ورودی غایب را بررسی می‌کنند. پس از افزودن adapter، اجرای Java 21 با `./mvnw -o -B -ntp -Psqlserver-it verify` در ۲۱٫۶۷۳ ثانیه، ۲۹۱ تست مستقل از زیرساخت و ۱۰۶ تست SQL Server را بدون failure، error یا skip گذراند. اجرای regression دیتابیس سوییچ سیاست را در حالت پیش‌فرض غیرفعال دارد؛ binding فعال با contextهای مستقل از زیرساخت بررسی شد. این نتایج شاهد عملیات کامل credit نیستند. محیط ARM64 دیتابیس همچنان emulation بدون پشتیبانی رسمی است.
