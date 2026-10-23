-- Run as the local administrator only after V1, once in each intended database.
SET NOCOUNT ON;
IF DB_NAME() NOT IN (N'cbs_dev', N'cbs_test') THROW 51000, 'Unexpected database for CBS runtime grants', 1;
IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL OR OBJECT_ID(N'dbo.operations', N'U') IS NULL
    OR OBJECT_ID(N'dbo.journals', N'U') IS NULL OR OBJECT_ID(N'dbo.postings', N'U') IS NULL
    THROW 51000, 'Apply V1 before runtime grants', 1;
GRANT SELECT ON OBJECT::dbo.accounts TO cbs_app;
GRANT SELECT, INSERT ON OBJECT::dbo.operations TO cbs_app;
GRANT SELECT, INSERT ON OBJECT::dbo.journals TO cbs_app;
GRANT SELECT, INSERT ON OBJECT::dbo.postings TO cbs_app;
GRANT UPDATE (balance) ON OBJECT::dbo.accounts TO cbs_app;
GRANT UPDATE (funding_account_id, funding_policy_version, funded_fingerprint, state, rejection_reason,
    response_format_version, http_status, response_content_type, response_body, completed_at)
    ON OBJECT::dbo.operations TO cbs_app;
GRANT SELECT, INSERT ON OBJECT::dbo.accounts TO cbs_initializer;
-- Omitted permissions are intentional. Do not add db_datawriter, db_owner or schema-wide runtime grants.
