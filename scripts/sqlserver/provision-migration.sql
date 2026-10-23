-- Local infrastructure identities, not business DDL. Secrets arrive through validated sqlcmd variables.
SET NOCOUNT ON;
USE master;
GO
IF SUSER_ID(N'cbs_migrator') IS NULL
    CREATE LOGIN cbs_migrator WITH PASSWORD = N'$(CBS_MIGRATOR_PASSWORD)', CHECK_POLICY = ON;
IF SUSER_ID(N'cbs_initializer') IS NULL
    CREATE LOGIN cbs_initializer WITH PASSWORD = N'$(CBS_INITIALIZER_PASSWORD)', CHECK_POLICY = ON;
GO
USE cbs_dev;
GO
IF USER_ID(N'cbs_migrator') IS NULL CREATE USER cbs_migrator FOR LOGIN cbs_migrator;
IF USER_ID(N'cbs_initializer') IS NULL CREATE USER cbs_initializer FOR LOGIN cbs_initializer;
IF SCHEMA_ID(N'cbs_migrations') IS NULL EXEC(N'CREATE SCHEMA cbs_migrations AUTHORIZATION cbs_migrator');
GRANT CONNECT, CREATE TABLE TO cbs_migrator;
GRANT ALTER, REFERENCES, VIEW DEFINITION ON SCHEMA::dbo TO cbs_migrator;
GRANT CONNECT TO cbs_initializer;
GO
USE cbs_test;
GO
IF USER_ID(N'cbs_migrator') IS NULL CREATE USER cbs_migrator FOR LOGIN cbs_migrator;
IF USER_ID(N'cbs_initializer') IS NULL CREATE USER cbs_initializer FOR LOGIN cbs_initializer;
IF SCHEMA_ID(N'cbs_migrations') IS NULL EXEC(N'CREATE SCHEMA cbs_migrations AUTHORIZATION cbs_migrator');
GRANT CONNECT, CREATE TABLE TO cbs_migrator;
GRANT ALTER, REFERENCES, VIEW DEFINITION ON SCHEMA::dbo TO cbs_migrator;
GRANT CONNECT TO cbs_initializer;
GO
