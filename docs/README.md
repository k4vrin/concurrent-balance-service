# Documentation / مستندات

[Project README](../README.md) · [README فارسی](../README.fa.md)

Use the current API reference and HTTP walkthrough to run the completed core. Dated implementation/evidence pages record the scope of their original checkpoint; the core acceptance page describes the final gate. برای اجرای هستهٔ تکمیل‌شده از مرجع فعلی API و walkthrough استفاده کنید؛ صفحه‌های شواهد تاریخ‌دار، checkpoint تاریخی خود را شرح می‌دهند.

## Run and use / اجرا و استفاده

| Topic / موضوع | English | فارسی |
| --- | --- | --- |
| Local startup and HTTP examples / راه‌اندازی و مثال‌ها | [HTTP walkthrough](en/HTTP-samples.md) | [نمونه‌های HTTP](fa/HTTP-samples.md) |
| Current API contract / قرارداد فعلی API | [API reference](en/API-v1.md) | [مرجع API](fa/API-v1.md) |
| SQL Server container / کانتینر | [Docker](en/Docker.md) | [Docker](fa/Docker.md) |
| Migrations and restricted identities / migration و مجوزها | [Schema](en/Schema.md) | [Schema](fa/Schema.md) |
| Test database settings / تنظیمات تست | [SQL Server tests](en/SQL-server-tests.md) | [تست‌های SQL Server](fa/SQL-server-tests.md) |
| Windows environment / محیط Windows | [Windows](en/Windows-sqlserver.md) | [Windows](fa/Windows-sqlserver.md) |

Executable collection: [balance-service.http](../http/balance-service.http). Zero-account setup: [create-demo-accounts.sql](../http/create-demo-accounts.sql). These files contain no credentials. / فایل‌های اجرایی بالا credential ندارند.

## Understand the design / شناخت طراحی

| Topic / موضوع | English | فارسی |
| --- | --- | --- |
| Ports, adapters and feature boundaries / مرزهای معماری | [Architecture walkthrough](en/Hexagonal-architecture.md) | [walkthrough معماری](fa/Hexagonal-architecture.md) |
| Double-entry scope / دفترکل دوطرفه | [Ledger](en/Ledger.md) | [دفترکل](fa/Ledger.md) |
| Projection calculation / محاسبهٔ projection | [Journal projection](en/Journal-projection.md) | [projection دفترکل](fa/Journal-projection.md) |
| Identity and atomic completion / هویت و پایان اتمیک | [Transaction protocol](en/Transaction-protocol.md) | [پروتکل تراکنش](fa/Transaction-protocol.md) |
| Canonical intent / intent canonical | [Canonical design](en/Canonical-design.md) | [طراحی canonical](fa/Canonical-design.md) |
| Retry and unknown commit / retry و commit نامعلوم | [Retry execution](en/Retry-execution.md) | [اجرای retry](fa/Retry-execution.md) |
| Accepted decisions / تصمیم‌های پذیرفته‌شده | [Decisions](en/Decisions.md) | [تصمیم‌ها](fa/Decisions.md) |

## Review evidence and contribute / شواهد و مشارکت

| Topic / موضوع | English | فارسی |
| --- | --- | --- |
| Completed core and reproducible verification / پذیرش هسته | [Core acceptance](en/Core-acceptance.md) | [پذیرش هسته](fa/Core-acceptance.md) |
| Cross-instance duplicate/conflict races / race هویت | [Cross-instance replay](en/Cross-instance-replay.md) | [replay میان instanceها](fa/Cross-instance-replay.md) |
| Opposite transfers and independent progress / انتقال معکوس و پیشرفت | [Transfer progress](en/Transfer-progress.md) | [پیشرفت انتقال](fa/Transfer-progress.md) |
| Commit/response faults / fault مرز commit و پاسخ | [Recovery acceptance](en/Recovery-acceptance.md) | [پذیرش recovery](fa/Recovery-acceptance.md) |
| Historical implementation checkpoints / checkpointهای اجرا | [Implementation status](en/Implementation-status.md) | [وضعیت پیاده‌سازی](fa/Implementation-status.md) |
| Later extensions / توسعه‌های بعدی | [Roadmap](en/Roadmap.md) | [نقشهٔ راه](fa/Roadmap.md) |
| Engineering and Git conventions / قواعد مهندسی و Git | [Workflow](en/Workflow.md) | [روند کار](fa/Workflow.md) |

Repository-wide instructions: [AGENTS.md](../AGENTS.md). Automated checks: [GitHub workflow](../.github/workflows/build.yml). The trusted local core is distinct from future authentication/performance/OTP/Redis/Kafka/CDC/FX delivery. / هستهٔ محلی مورداعتماد از تحویل توسعه‌های بعدی جداست.
