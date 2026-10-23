# بیت‌های UUID، مقایسهٔ unsigned و ترتیب قفل حساب

[English](../en/UUID-lock-order.md) · [معماری](Architecture.md) · [پروتکل تراکنش](Transaction-protocol.md)

راهنمای آموزشی در ۲۰۲۶-۱۰-۲۳. این سند AccountId، comparator موجود در AccountLockOrder و شش تست آن را توضیح می‌دهد. comparator تست واحد دارد؛ نگاشت UUID و قفل SQL Server پیاده نشده‌اند. مرتب‌کردن شناسه به‌تنهایی قفل نمی‌گیرد و حذف همهٔ deadlockها را تضمین نمی‌کند.

## ۱. چرا این موضوع در پروژه مطرح شد؟

AccountId به UUID معنای دامنه می‌دهد و آن را از شناسه‌های دیگر جدا می‌کند. همهٔ writerهای حساب باید مجموعهٔ کامل حساب‌های درگیر را با یک ترتیب مشخص قفل کنند. سیاست منتخب ما unsigned نزولی است و به جهت انتقال وابسته نیست؛ ترتیب تجاری یا زمان ایجاد حساب را نشان نمی‌دهد.

عبارت مبهم «high-half sign bit» یعنی چپ‌ترین بیت از ۶۴ بیت اول UUID. خود UUID شناسه است؛ تفسیر signed در نوع long جاوا باعث می‌شود دربارهٔ علامت هر نیمه حرف بزنیم.

## ۲. رقم hexadecimal با بیت فرق دارد

مبنای دو فقط 0 و 1 دارد. مبنای شانزده یا hex از 0 تا 9 و a تا f استفاده می‌کند. هر رقم hex چهار بیت است، چون 16 = 2^4. حروف بزرگ و کوچک همان ارزش عددی را دارند.

| Hex | Binary | Decimal |
| --- | --- | --- |
| 0 | 0000 | 0 |
| 1 | 0001 | 1 |
| 2 | 0010 | 2 |
| 3 | 0011 | 3 |
| 4 | 0100 | 4 |
| 5 | 0101 | 5 |
| 6 | 0110 | 6 |
| 7 | 0111 | 7 |
| 8 | 1000 | 8 |
| 9 | 1001 | 9 |
| a | 1010 | 10 |
| b | 1011 | 11 |
| c | 1100 | 12 |
| d | 1101 | 13 |
| e | 1110 | 14 |
| f | 1111 | 15 |

رقم 8 در hex برابر 1000 در binary است. پس رقم سمت چپ 8 است، اما بیت سمت چپ 1 است. پیشوند 0x در literal جاوا فقط مبنا را نشان می‌دهد و جزو بیت ذخیره‌شده نیست.

شانزده رقم hex برابر ۶۴ بیت است. هر خط زیر شانزده گروه چهاربیتی دارد؛ صفر ابتدایی در نمایش با طول ثابت باید حفظ شود:

```text
8000000000000000
1000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
^
leftmost bit = 1

7fffffffffffffff
0111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111
^
leftmost bit = 0
```

## ۳. long علامت‌دار و two’s complement

نوع long در جاوا ۶۴ بیت و بازهٔ -2^63 تا 2^63 - 1 دارد. در تفسیر signed، بیت چپ نقش sign bit دارد: صفر یعنی صفر/مثبت و یک یعنی منفی. این نمایش two’s complement است، نه یک علامت منفی جدا و بعد قدرمطلق.

اگر ارزش unsigned همان الگو را U بنامیم:

```text
If U < 2^63:  signed value = U
Otherwise:   signed value = U - 2^64
```

| الگوی hex ۶۴ بیتی | مقدار signed | مقدار unsigned |
| --- | --- | --- |
| 0000000000000000 | 0 | 0 |
| 7fffffffffffffff | 9,223,372,036,854,775,807 | 9,223,372,036,854,775,807 |
| 8000000000000000 | -9,223,372,036,854,775,808 | 9,223,372,036,854,775,808 |
| ffffffffffffffff | -1 | 18,446,744,073,709,551,615 |

بنابراین literal برابر `0x7fff_ffff_ffff_ffffL` همان Long.MAX_VALUE و `0x8000_0000_0000_0000L` همان Long.MIN_VALUE است. underscore فقط خوانایی را بهتر می‌کند. literal شانزدهی با بیت علامت روشن می‌تواند بدون نوشتن منفی، long منفی باشد.

در signed و unsigned بیت‌ها یکی‌اند؛ تفسیر فرق دارد. Long.compareUnsigned بیت اول را حذف، صفر یا معکوس نمی‌کند. تمام ۶۴ بیت را بدون معنای علامت منفی مقایسه می‌کند و primitive جدا لازم ندارد. برای نمایش unsigned از Long.toUnsignedString استفاده می‌کنیم؛ چاپ ده‌دهی عادی long همچنان signed است.

## ۴. دو نیمهٔ UUID

UUID صدوبیست‌وهشت بیت است و معمولاً با ۳۲ رقم hex در گروه‌های 8-4-4-4-12 نمایش داده می‌شود. خط تیره فقط قالب است. سه گروه اول ۱۶ رقم و نیمهٔ بالایی‌اند؛ دو گروه آخر نیمهٔ پایینی‌اند. high/low به اهمیت عددی اشاره دارد، نه آدرس حافظه یا byte order پردازنده.

```text
UUID:      80000000-0000-0000-0000-000000000000
High half: 8000000000000000   (first 16 hex digits, ignoring hyphens)
Low half:  0000000000000000   (last 16 hex digits)

UUID:      00000000-0000-0000-8000-000000000000
High half: 0000000000000000
Low half:  8000000000000000
```

متد getMostSignificantBits نیمهٔ بالا و getLeastSignificantBits نیمهٔ پایین را می‌دهد. خروجی هر دو long علامت‌دار با همان بیت‌هاست؛ منفی چاپ‌شدن یک نیمه، UUID را نامعتبر نمی‌کند.

ترتیب unsigned صعودی ما از نظر مفهومی این مقدار را مقایسه می‌کند:

```text
unsignedUUID = unsigned(highHalf) * 2^64 + unsigned(lowHalf)
```

در اجرا این عدد بزرگ را نمی‌سازیم. اول نیمهٔ بالا و فقط در صورت برابری نیمهٔ پایین را می‌سنجیم. اگر نیمهٔ بالا متفاوت باشد، نیمهٔ پایین نتیجه را عوض نمی‌کند.

## ۵. چرا مقایسهٔ signed نتیجهٔ دیگری می‌دهد؟

برای دو نیمهٔ 8000000000000000 و 7fffffffffffffff:

```text
Signed:    -9223372036854775808 < 9223372036854775807
Unsigned:   9223372036854775808 > 9223372036854775807
```

مقایسهٔ signed معمولی با سیاست unsigned ما در این مرز ناسازگار است. تست نیمهٔ بالا عمداً نیمهٔ پایین UUID با بالای کوچک‌تر را تمام f می‌گذارد تا ثابت کند پایین نمی‌تواند تصمیم بالا را برگرداند. تست tie-breaker بالا را برابر می‌گذارد و فقط بیت علامت پایین را تغییر می‌دهد.

اگر همه عمداً یک ترتیب signed ثابت انتخاب می‌کردند، آن هم می‌توانست سیاست قفل سازگار باشد. مشکل، قاطی‌کردن قراردادهاست. ما unsigned نزولی را پذیرفته‌ایم و همهٔ writerها باید همان را اجرا کنند.

## ۶. خواندن comparator

منطق فعلی account/application/AccountLockOrder.java:

```java
private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
    int comparison = Long.compareUnsigned(
            left.getMostSignificantBits(), right.getMostSignificantBits());
    return comparison != 0 ? comparison : Long.compareUnsigned(
            left.getLeastSignificantBits(), right.getLeastSignificantBits());
};

public static final Comparator<AccountId> DESCENDING =
        Comparator.comparing(AccountId::value, UNSIGNED_UUID_ORDER).reversed();
```

1. نیمهٔ بالا unsigned مقایسه می‌شود.
2. در صورت تفاوت، همان نتیجه برمی‌گردد.
3. در برابری بالا، پایین unsigned مقایسه می‌شود.
4. UUID از wrapper نوع AccountId استخراج می‌شود.
5. کل comparator صعودی معکوس می‌شود تا نزولی باشد.

علامت نتیجهٔ comparator محل قرارگرفتن را می‌گوید، نه علامت پول:

| نتیجه | محل آرگومان چپ |
| --- | --- |
| منفی | پیش از آرگومان راست |
| صفر | برابر در ترتیب |
| مثبت | پس از آرگومان راست |

در DESCENDING، UUID بزرگ‌تر جلوتر می‌آید؛ پس compare بزرگ‌تر با کوچک‌تر نتیجهٔ منفی دارد. این منفی به معنی شناسه یا مبلغ منفی نیست.

## ۷. هر تست از چه خطایی محافظت می‌کند؟

| تست | خطای قابل‌کشف |
| --- | --- |
| comparesHighBitsAsUnsigned | مقایسهٔ signed الگوی بالایی 0x8000 را پایین‌تر از 0x7fff می‌گذارد |
| comparesLowBitsAsUnsignedWhenHighBitsMatch | نیمهٔ بالا درست ولی tie-breaker پایین signed یا حذف شده است |
| equalityUsesUuidValue | هویت شیء wrapper به‌جای ارزش UUID در ترتیب دخالت دارد |
| oppositeTransferDirectionsProduceSameOrder | نقش مبدأ/مقصد ترتیب را عوض می‌کند |
| ordersBothHalvesAcrossExtremesTransitively | ترتیب ناسازگار در مرزها یا tie-breaker؛ تمام جفت‌های fixture محدود را بررسی می‌کند |
| rejectsMissingAccountIds | null-first/null-last ورودی ناموجود را پنهانی قبول می‌کند |

این حالت‌ها contract را روی fixtureهای مهم بررسی می‌کنند، نه روی تمام UUIDهای ممکن. وجود حساب، مجوز، اخذ قفل و concurrency دیتابیس تست نشده است. UUID صفر یا تمام f در تست الگوی بیتی است؛ تولید حساب واقعی یا UUID نسخهٔ ۴ را ادعا نمی‌کند. wrapper فعلی نسخهٔ UUID را محدود نمی‌کند.

## ۸. ارتباط با جلوگیری از deadlock

اگر A با 1000 و B با f000 شروع شوند، هر دو جهت A→B و B→A به ترتیب B سپس A می‌رسند. الگوریتم مبدأمحور می‌توانست جهت مخالف را با ترتیب مخالف قفل کند؛ comparator مشترک این وارونگی مشخص را حذف می‌کند.

مسیر persistence آینده باید تمام حساب‌های مشتری/سیستمی را جمع، تکراری‌ها را حذف و با AccountLockOrder.DESCENDING مرتب کند؛ lookupهای قفل پشت سر هم در همان تراکنش اجرا شوند. پیش از کامل‌شدن ترتیب، یک حساب را جدا قفل نکنیم. comparator سیاست قفل در application است؛ AccountId عمداً Comparable نیست تا ترتیب تجاری عمومی القا نکند.

ترتیب uniqueidentifier در SQL Server معنای خودش را دارد. ترتیب طبیعی UUID جاوا، sort سرور یا نتیجهٔ ORDER BY جای تضمین اخذ فیزیکی قفل نیست. SQL ایندکس‌شده، عمر قفل و retry تست واقعی می‌خواهند. منابع index/constraint و سایر writerها با وجود ترتیب درست هم می‌توانند deadlock ایجاد کنند.

## ۹. مثال قابل‌اجرا با Java 21

پس از build، کد زیر را با نام UuidOrderingDemo.java بیرون source مخزن ذخیره کنید:

```java
import java.util.UUID;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.domain.AccountId;

public class UuidOrderingDemo {
    public static void main(String[] args) {
        long high = 0x8000_0000_0000_0000L;
        long low = 0x7fff_ffff_ffff_ffffL;

        System.out.println("signed high: " + high);
        System.out.println("unsigned high: " + Long.toUnsignedString(high));
        System.out.println("signed low: " + low);
        System.out.println("signed comparison: " + Long.compare(high, low));
        System.out.println("unsigned comparison: " + Long.compareUnsigned(high, low));

        var first = new AccountId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
        var second = new AccountId(UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"));
        System.out.println("descending UUID comparison: "
                + AccountLockOrder.DESCENDING.compare(first, second));
    }
}
```

از ریشهٔ مخزن با Java 21 اجرا شود:

```sh
java --class-path target/classes /path/to/UuidOrderingDemo.java
```

خروجی موردانتظار که در ۲۰۲۶-۱۰-۲۳ با Java 21 بررسی شد:

```text
signed high: -9223372036854775808
unsigned high: 9223372036854775808
signed low: 9223372036854775807
signed comparison: -1
unsigned comparison: 1
descending UUID comparison: -1
```

نتیجهٔ compare موجودی حساب نیست. این مثال حساب نمی‌سازد و اتصال دیتابیس نمی‌خواهد.

## ۱۰. دفاع در مصاحبه و خطاهای رایج

پاسخ کوتاه: «AccountId برای ایمنی نوع، UUID را می‌پوشاند. دو نیمه را unsigned و ابتدا بالا مقایسه می‌کنیم و برای ترتیب مشترک نزولی نتیجه را معکوس می‌کنیم. دو جهت انتقال و writer حساب تأمین همین ترتیب قفل را دارند. این کار deadlock ترتیب حساب را کاهش می‌دهد؛ تست SQL و retry کل تراکنش برای خرابی‌های دیگر همچنان لازم‌اند.»

رقم hex را با بیت، کل UUID را با مبلغ signed، یا علامت نتیجهٔ comparator را با منفی‌بودن شناسه اشتباه نگیریم. بیت علامت را حذف نکنیم، فقط نیمهٔ بالا را نسنجیم و برای مقایسه از تفریق پرخطر استفاده نکنیم. یکتایی UUID، قید یکتای دیتابیس، مجوز و idempotency تضمین‌های جدا هستند.

## منابع و کد مرتبط

مرور در ۲۰۲۶-۱۰-۲۳: [Long در Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html)، [UUID](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/UUID.html)، [uniqueidentifier در SQL Server](https://learn.microsoft.com/en-us/sql/t-sql/data-types/uniqueidentifier-transact-sql). محاسبهٔ بالا قرارداد منتخب ما را توضیح می‌دهد؛ مستند vendor آن سیاست را برای پروژه انتخاب نمی‌کند.

[کد comparator](../../src/main/java/dev/kavrin/balance/account/application/AccountLockOrder.java) · [تست‌ها](../../src/test/java/dev/kavrin/balance/account/application/AccountLockOrderTests.java) · [شناسهٔ حساب](../../src/main/java/dev/kavrin/balance/account/domain/AccountId.java)
