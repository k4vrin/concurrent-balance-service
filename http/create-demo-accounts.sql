-- Development fixtures for balance-service.http. Run as cbs_initializer after V1/grants.
-- Insert only zero accounts; all money enters through the HTTP credit route.
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> N'cbs_dev' THROW 51000, 'HTTP demo requires cbs_dev', 1;
IF CURRENT_USER <> N'cbs_initializer' OR IS_SRVROLEMEMBER('sysadmin') <> 0 OR IS_MEMBER('db_owner') <> 0
    THROW 51000, 'Use the restricted cbs_initializer identity', 1;

BEGIN TRANSACTION;
-- Match V1 collation explicitly; a table variable otherwise inherits the catalog default.
DECLARE @accounts TABLE (account_id uniqueidentifier PRIMARY KEY, category varchar(32) COLLATE Latin1_General_100_BIN2);
INSERT @accounts VALUES
    ('90000000-0000-4000-8000-000000000001', 'FUNDING_CLEARING'),
    ('10000000-0000-4000-8000-000000000001', 'CUSTOMER_LIABILITY'),
    ('20000000-0000-4000-8000-000000000002', 'CUSTOMER_LIABILITY');

-- Repeated setup retains balances and history. Refuse incompatible preexisting identities.
IF EXISTS (SELECT 1 FROM dbo.accounts a JOIN @accounts d ON a.account_id=d.account_id
    WHERE a.category<>d.category OR a.currency<>N'IRR' OR a.status<>'ACTIVE')
BEGIN
    ROLLBACK;
    THROW 51000, 'Existing demo account has incompatible category, currency or status', 1;
END;
INSERT dbo.accounts(account_id,category,currency)
    SELECT d.account_id,d.category,N'IRR' FROM @accounts d
    WHERE NOT EXISTS (SELECT 1 FROM dbo.accounts a WHERE a.account_id=d.account_id);
COMMIT;
SELECT a.account_id,a.category,a.currency,a.balance FROM dbo.accounts a
    JOIN @accounts d ON a.account_id=d.account_id ORDER BY a.account_id;
