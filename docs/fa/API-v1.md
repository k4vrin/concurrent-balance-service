# مرجع HTTP API نسخهٔ اول

[English](../en/API-v1.md) · [درخواست‌های اجرایی](../../http/balance-service.http) · [walkthrough محلی](HTTP-samples.md) · [پذیرش هسته](Core-acceptance.md)

این صفحه قرارداد پیاده‌شدهٔ هستهٔ CBS20 است. با دیتابیس/schema آماده و `EXECUTION_POLICY_ENABLED=true`، هر چهار مسیر روی SQL Server اجرا می‌شوند. سرور به‌صورت پیش‌فرض روی `http://127.0.0.1:8080` گوش می‌دهد و اپراتور محلی مورداعتماد را فرض می‌کند؛ احراز هویت و مالکیت حساب محدودهٔ بعدی‌اند. endpoint عمومی ساخت حساب یا موجودی افتتاحیه نداریم.

## مسیرها و body درخواست

درخواست POST به `Content-Type: application/json` نیاز دارد. موفقیت با `application/json` و problem با `application/problem+json` برمی‌گردد. برای بررسی هر دو مسیر از `Accept: application/json, application/problem+json` استفاده کنید.

| متد | مسیر | فیلدهای اجباری JSON |
| --- | --- | --- |
| POST | `/api/v1/accounts/{accountId}/credits` | `transactionId`، `amountRial`، `currency` |
| POST | `/api/v1/accounts/{accountId}/debits` | `transactionId`، `amountRial`، `currency` |
| POST | `/api/v1/transfers` | `transactionId`، `sourceAccountId`، `destinationAccountId`، `amountRial`، `currency` |
| GET | `/api/v1/accounts/{accountId}/balance` | بدون body. |

credit/debit حساب مشتری را در path می‌گیرند. سرور حساب funding را از تنظیمات انتخاب می‌کند و فراخواننده نمی‌تواند آن را تعیین کند. transfer میان دو حساب مشتری متمایز و بدون مسیر funding انجام می‌شود. حساب‌های درگیر باید موجود و فعال باشند و قواعد category/currency را رعایت کنند.

نمونهٔ credit پس از ساخت حساب:

```http
POST http://127.0.0.1:8080/api/v1/accounts/10000000-0000-4000-8000-000000000001/credits
Content-Type: application/json

{"transactionId":"demo-v1-credit-alice","amountRial":1000,"currency":"IRR"}
```

نمونهٔ transfer:

```json
{
  "transactionId": "demo-v1-transfer",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "20000000-0000-4000-8000-000000000002",
  "amountRial": 250,
  "currency": "IRR"
}
```

## قواعد parsing و مبلغ

- همهٔ فیلدهای جدول اجباری و non-null هستند. property ناشناخته، کلید تکراری، JSON اضافه پس از body و JSON خراب رد می‌شوند.
- `transactionId` رشتهٔ JSON حساس به بزرگی حروف با طول ۱ تا ۱۲۸ است و فقط حروف ASCII، رقم و `.`, `_`, `:`, `-` را می‌پذیرد. trim، normalization، alias در header یا مقدار تولیدشدهٔ پیش‌فرض نداریم.
- شناسهٔ حساب باید UUID با شکل کامل گروه‌ها باشد. بزرگی حروف hex هویت UUID را عوض نمی‌کند. گروه کوتاه نامعتبر است و transfer برای رد self-transfer مقدار UUID parse‌شده را مقایسه می‌کند.
- `amountRial` باید token عدد صحیح مثبت JSON در محدودهٔ `long` باشد. صفر، منفی، `1.0`، نمایش نمایی، رشتهٔ عددی، boolean و overflow نامعتبرند. هر واحد یک ریال است و ضرب در ۱۰۰ یا گردکردن بی‌صدا نداریم.
- `currency` باید کد ISO دقیق uppercase باشد که Java می‌شناسد. سیاست اجرای فعلی **فقط IRR** را می‌پذیرد. معتبر بودن syntax ارز دیگر، اجرای آن یا قرارداد واحد جدیدی را فعال نمی‌کند.
- سقف پیش‌فرض هر تراکنش و موجودی نهایی مشتری **10000000000000 ریال** است. مبلغ مثبت قابل‌نمایشِ بالاتر از سقف، parsing را می‌گذراند و سپس رد کسب‌وکار ماندگار می‌گیرد. محاسبهٔ checked جداگانه از overflow جلوگیری می‌کند.

شکست parsing/shape پیش از اجرای application، پاسخ 400 `INVALID_REQUEST` دارد و ID رزرو نمی‌کند. ارز خارج از سیاست برای عملیات تازه، 400 `UNSUPPORTED_CURRENCY` بدون operation ماندگار دارد. نتیجهٔ نهایی موجود پیش از سیاست پذیرش فعلی بررسی می‌شود تا تغییر سیاست، replay منطبق را تغییر ندهد.

## پاسخ موفق

mutation **فقط پس از commit تراکنش** پاسخ 200 می‌دهد. acknowledgement حداقلی دقیقاً این فیلدها را دارد و موجودی فعلی، journal ID یا جزئیات persistence را برنمی‌گرداند:

```json
{"transactionId":"demo-v1-transfer","operationType":"TRANSFER","currency":"IRR","status":"SUCCEEDED"}
```

استعلام موجودی، projection مرجع commit‌شده را می‌دهد:

```json
{"accountId":"10000000-0000-4000-8000-000000000001","balanceRial":750,"currency":"IRR"}
```

موجودی به history اجراشده وابسته است؛ مقدار ۷۵۰، موجودی نهایی Alice در [walkthrough HTTP](HTTP-samples.md) است. حساب ناموجود 404 می‌گیرد و هیچ‌وقت صفر نمایش داده نمی‌شود. استعلام موجودی operation نمی‌سازد.

## قرارداد خطا

problem شامل `status` HTTP، عنوان استاندارد `title`، متن امن `detail`، مسیر `instance` و `code` پایدار است. serializer فعلی نوع پیش‌فرض `about:blank` را در JSON نمی‌آورد. رد ماندگار mutation، فیلدهای `transactionId`، `operationType` و `currency` هم دارد.

| HTTP | کد | معنا و اثر روی هویت |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | ورودی/shape خراب، UUID/مبلغ نامعتبر یا self-transfer؛ بدون operation ماندگار. |
| 400 | `UNSUPPORTED_CURRENCY` | شکست سیاست پذیرش عملیات تازه؛ بدون operation ماندگار. |
| 404 | `ACCOUNT_NOT_FOUND` | حساب ناموجود در استعلام، یا رد ماندگار mutation برای حساب درخواست‌شدهٔ ناموجود. |
| 409 | `TRANSACTION_ID_CONFLICT` | ID موجود با درخواست canonical متفاوت؛ پاسخ اصلی تغییر نمی‌کند. |
| 409 | `INSUFFICIENT_FUNDS` | رد ماندگار debit/transfer؛ بدون تاریخچهٔ مالی. |
| 409 | `TRANSACTION_LIMIT_EXCEEDED` | رد ماندگار مبلغ بالاتر از سقف تراکنش. |
| 409 | `BALANCE_LIMIT_EXCEEDED` | رد ماندگار موجودی بالاتر از سقف مشتری. |
| 409 | `BALANCE_OVERFLOW` | رد ماندگار projection خارج از محدودهٔ نمایش. |
| 503 | `OPERATION_UNAVAILABLE` | execution غیرفعال، خطای طبقه‌بندی‌شدهٔ زیرساخت، پایان recovery یا commit حل‌نشده. |
| 500 | `INTERNAL_ERROR` | defect پیش‌بینی‌نشده؛ فرض نکنید commit رخ نداده است. |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED` / `NOT_ACCEPTABLE` / `UNSUPPORTED_MEDIA_TYPE` | شکست متد یا مذاکرهٔ representation در HTTP. |

نمونهٔ رد ماندگار debit مبلغ ۶۵۱ در برابر موجودی ۶۵۰:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Available funds are insufficient.",
  "instance": "/api/v1/accounts/10000000-0000-4000-8000-000000000001/debits",
  "code": "INSUFFICIENT_FUNDS",
  "transactionId": "demo-v1-rejected-debit",
  "operationType": "DEBIT",
  "currency": "IRR"
}
```

منطق client را بر اساس متن `detail` ننویسید؛ status/code را بررسی کنید. SQL داخلی، credential و stack trace جزو پاسخ نیستند. category/status/currency نامناسب حساب و funding داخلی ناموجود، API مدیریت حساب نیستند؛ تنظیمات و fixture سرور را بررسی کنید و هر خطای فنی را رد مشتری ندانید.

## Replay و بازیابی خطا

فضای نام `transactionId` میان همهٔ mutationها مشترک است. درخواست canonical ماندگار، نوع عملیات، حساب‌های مشتری/درگیر، مبلغ و ارز را در بر می‌گیرد. intent منطبق همان status، content type و body سریال‌شدهٔ اصلی را می‌گیرد؛ نوع/payload متفاوت تعارض دارد. replay، journal یا posting اضافه نمی‌کند و پاسخ را از موجودی یا سیاست جدید بازسازی نمی‌کند.

رد نهایی کسب‌وکار هم ماندگار است. اگر `demo-v1-rejected-debit` مبلغ ۶۵۱ را در برابر ۶۵۰ درخواست کند و بعد credit جدا موجودی را به ۷۵۰ برساند، retry همان ID هنوز 409 اصلی را می‌گیرد. تلاش مالی تازه به ID تازه نیاز دارد.

هر mutation موفق، operation، journal/posting متوازن immutable، همهٔ projectionها و پاسخ اصلی را اتمیک commit می‌کند. رد فقط تصمیم ماندگار را commit می‌کند. rollback قطعی می‌تواند retry محدود کل تراکنش با همان ID داشته باشد؛ commit نامعلوم به‌جای اجرای کور دوباره، recovery read دارد. **پاسخ 503 یا 500، timeout یا گم‌شدن پاسخ ثابت نمی‌کند که commit رخ نداده است. برای بازیابی، ID و payload اصلی را نگه دارید.** [Retry/recovery](Retry-execution.md).

## تنظیمات و بررسی

راه‌اندازی به schema migrate‌شده و credentialهای runtime با `DB_URL`، `DB_USERNAME` و `DB_PASSWORD` نیاز دارد. فعال‌سازی mutation علاوه بر آن، UUID/version معتبر funding می‌خواهد؛ فرمان‌ها در [walkthrough نمونه](HTTP-samples.md) آمده‌اند. با persistence آماده و mutation غیرفعال، استعلام موجودی همچنان در دسترس است. runtime خودکار migration اجرا نمی‌کند.

[راهنمای پذیرش هسته](Core-acceptance.md)، شواهد واقعی HTTP تا SQL Server، rollback، replay، هم‌زمانی و restart process را ثبت می‌کند. checkpointهای تاریخی توسعه در [وضعیت پیاده‌سازی](Implementation-status.md) و صفحه‌های شواهد باقی‌اند. این مرجع فعلی جای توصیف‌های قبلیِ مسیرهای unavailable را می‌گیرد.
