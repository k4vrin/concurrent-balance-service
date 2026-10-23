-- Infrastructure only: do not introduce business tables or nonzero account balances here.
SET NOCOUNT ON;
USE [master];
GO
IF DB_ID(N'cbs_dev') IS NULL
    EXEC(N'CREATE DATABASE [cbs_dev]');
IF DB_ID(N'cbs_test') IS NULL
    EXEC(N'CREATE DATABASE [cbs_test]');
GO
IF SUSER_ID(N'cbs_app') IS NULL
    CREATE LOGIN [cbs_app] WITH PASSWORD = N'$(CBS_APP_PASSWORD)', CHECK_POLICY = ON;
GO
USE [cbs_dev];
GO
IF DATABASE_PRINCIPAL_ID(N'cbs_app') IS NULL
    CREATE USER [cbs_app] FOR LOGIN [cbs_app];
GRANT CONNECT TO [cbs_app];
GO
USE [cbs_test];
GO
IF DATABASE_PRINCIPAL_ID(N'cbs_app') IS NULL
    CREATE USER [cbs_app] FOR LOGIN [cbs_app];
GRANT CONNECT TO [cbs_app];
GO
USE [master];
GO
SELECT CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
       CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition,
       CAST(SERVERPROPERTY('Collation') AS nvarchar(128)) AS server_collation;
SELECT name, compatibility_level, collation_name,
       is_read_committed_snapshot_on, snapshot_isolation_state_desc
FROM sys.databases WHERE name IN (N'cbs_dev', N'cbs_test');
SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID;
GO

SELECT N'cbs_dev' AS database_name, COUNT(*) AS user_table_count
FROM [cbs_dev].sys.tables WHERE is_ms_shipped = 0
UNION ALL
SELECT N'cbs_test', COUNT(*) FROM [cbs_test].sys.tables WHERE is_ms_shipped = 0;
GO
