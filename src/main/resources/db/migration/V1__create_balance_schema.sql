-- Row constraints protect storage shape. Balanced journals, terminal completeness and
-- projection equality remain controlled-transaction invariants under ADR-007/ADR-010.
CREATE TABLE dbo.accounts (
    account_id uniqueidentifier NOT NULL CONSTRAINT pk_accounts PRIMARY KEY,
    category varchar(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    currency nchar(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    balance bigint NOT NULL CONSTRAINT df_accounts_balance DEFAULT 0,
    status varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT df_accounts_status DEFAULT 'ACTIVE',
    created_at datetime2(7) NOT NULL CONSTRAINT df_accounts_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_accounts_id_currency UNIQUE (account_id, currency),
    CONSTRAINT ck_accounts_category CHECK (category IN ('CUSTOMER_LIABILITY', 'FUNDING_CLEARING') AND DATALENGTH(category) = LEN(category)),
    CONSTRAINT ck_accounts_currency CHECK (currency LIKE N'[A-Z][A-Z][A-Z]'),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED') AND DATALENGTH(status) = LEN(status)),
    CONSTRAINT ck_accounts_balance CHECK (category <> 'CUSTOMER_LIABILITY' OR balance >= 0)
);

CREATE TABLE dbo.operations (
    operation_id uniqueidentifier NOT NULL CONSTRAINT pk_operations PRIMARY KEY,
    transaction_id nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    operation_type varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_account_id uniqueidentifier NULL,
    destination_account_id uniqueidentifier NULL,
    amount bigint NOT NULL,
    currency nchar(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    canonical_format_version int NOT NULL,
    canonical_request varbinary(max) NOT NULL,
    canonical_fingerprint binary(32) NOT NULL,
    execution_policy_version nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    funding_account_id uniqueidentifier NULL,
    funding_policy_version nvarchar(128) COLLATE Latin1_General_100_BIN2 NULL,
    funded_fingerprint binary(32) NULL,
    state varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    rejection_reason varchar(64) COLLATE Latin1_General_100_BIN2 NULL,
    response_format_version int NULL,
    http_status smallint NULL,
    response_content_type varchar(64) COLLATE Latin1_General_100_BIN2 NULL,
    response_body varbinary(max) NULL,
    created_at datetime2(7) NOT NULL CONSTRAINT df_operations_created_at DEFAULT SYSUTCDATETIME(),
    completed_at datetime2(7) NULL,
    CONSTRAINT uq_operations_transaction_id UNIQUE (transaction_id),
    CONSTRAINT uq_operations_id_currency UNIQUE (operation_id, currency),
    CONSTRAINT ck_operations_transaction_id_length CHECK (DATALENGTH(transaction_id) BETWEEN 2 AND 256),
    -- Preserve Unicode until validation, including NUL and trailing spaces; see Transaction-id-storage.md.
    CONSTRAINT ck_operations_transaction_id_characters CHECK (
        CONVERT(varbinary(512), TRANSLATE(transaction_id COLLATE Latin1_General_100_BIN2,
            N'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-', REPLICATE(N'A', 66)))
        = CONVERT(varbinary(512), REPLICATE(N'A', DATALENGTH(transaction_id) / 2))),
    CONSTRAINT ck_operations_type CHECK (operation_type IN ('CREDIT', 'DEBIT', 'TRANSFER', 'OPENING') AND DATALENGTH(operation_type) = LEN(operation_type)),
    -- Request IDs deliberately have no account FK: reserve before ordered locks and retain missing-account rejection.
    CONSTRAINT ck_operations_shape CHECK (
        (operation_type IN ('CREDIT', 'OPENING') AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (operation_type = 'DEBIT' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
        OR (operation_type = 'TRANSFER' AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id <> destination_account_id)),
    CONSTRAINT ck_operations_amount CHECK (amount > 0),
    CONSTRAINT ck_operations_currency CHECK (currency LIKE N'[A-Z][A-Z][A-Z]'),
    CONSTRAINT ck_operations_canonical CHECK (canonical_format_version = 1 AND DATALENGTH(canonical_request) > 0
        AND canonical_fingerprint = HASHBYTES('SHA2_256', canonical_request)),
    CONSTRAINT ck_operations_policy CHECK (LEN(execution_policy_version) > 0),
    CONSTRAINT ck_operations_funding CHECK (
        (funding_account_id IS NULL AND funding_policy_version IS NULL AND funded_fingerprint IS NULL)
        OR (funding_account_id IS NOT NULL AND funding_policy_version IS NOT NULL AND LEN(funding_policy_version) > 0
            AND funded_fingerprint IS NOT NULL AND operation_type <> 'TRANSFER')),
    CONSTRAINT ck_operations_success_funding CHECK (state <> 'SUCCEEDED' OR operation_type = 'TRANSFER' OR funding_account_id IS NOT NULL),
    CONSTRAINT ck_operations_state CHECK (state IN ('PENDING', 'SUCCEEDED', 'REJECTED') AND DATALENGTH(state) = LEN(state)),
    CONSTRAINT ck_operations_rejection_reason CHECK (rejection_reason IS NULL OR
        (rejection_reason IN ('ACCOUNT_NOT_FOUND', 'INSUFFICIENT_FUNDS', 'BALANCE_OVERFLOW', 'TRANSACTION_LIMIT_EXCEEDED', 'BALANCE_LIMIT_EXCEEDED')
            AND DATALENGTH(rejection_reason) = LEN(rejection_reason))),
    CONSTRAINT ck_operations_outcome CHECK (
        (state = 'PENDING' AND completed_at IS NULL AND rejection_reason IS NULL AND response_format_version IS NULL
            AND http_status IS NULL AND response_content_type IS NULL AND response_body IS NULL)
        OR (state = 'SUCCEEDED' AND completed_at IS NOT NULL AND rejection_reason IS NULL AND response_format_version IS NOT NULL
            AND response_format_version = 1 AND http_status IS NOT NULL AND http_status = 200
            AND response_content_type IS NOT NULL AND response_content_type = 'application/json'
            AND DATALENGTH(response_content_type) = 16 AND response_body IS NOT NULL AND DATALENGTH(response_body) > 0)
        OR (state = 'REJECTED' AND completed_at IS NOT NULL AND rejection_reason IS NOT NULL AND response_format_version IS NOT NULL
            AND response_format_version = 1 AND http_status IS NOT NULL AND http_status BETWEEN 400 AND 499
            AND response_content_type IS NOT NULL AND response_content_type = 'application/problem+json'
            AND DATALENGTH(response_content_type) = 24 AND response_body IS NOT NULL AND DATALENGTH(response_body) > 0)),
    CONSTRAINT ck_operations_completion_time CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE TABLE dbo.journals (
    journal_id uniqueidentifier NOT NULL CONSTRAINT pk_journals PRIMARY KEY,
    operation_id uniqueidentifier NOT NULL,
    currency nchar(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    created_at datetime2(7) NOT NULL CONSTRAINT df_journals_created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_journals_operation_id UNIQUE (operation_id),
    CONSTRAINT uq_journals_id_currency UNIQUE (journal_id, currency),
    CONSTRAINT fk_journals_operation_currency FOREIGN KEY (operation_id, currency)
        REFERENCES dbo.operations (operation_id, currency) ON DELETE NO ACTION,
    CONSTRAINT ck_journals_currency CHECK (currency LIKE N'[A-Z][A-Z][A-Z]')
);

CREATE TABLE dbo.postings (
    journal_id uniqueidentifier NOT NULL,
    line_number int NOT NULL,
    account_id uniqueidentifier NOT NULL,
    currency nchar(3) COLLATE Latin1_General_100_BIN2 NOT NULL,
    side varchar(6) COLLATE Latin1_General_100_BIN2 NOT NULL,
    amount bigint NOT NULL,
    CONSTRAINT pk_postings PRIMARY KEY (journal_id, line_number),
    CONSTRAINT fk_postings_journal_currency FOREIGN KEY (journal_id, currency)
        REFERENCES dbo.journals (journal_id, currency) ON DELETE NO ACTION,
    CONSTRAINT fk_postings_account_currency FOREIGN KEY (account_id, currency)
        REFERENCES dbo.accounts (account_id, currency) ON DELETE NO ACTION,
    CONSTRAINT ck_postings_line CHECK (line_number > 0),
    CONSTRAINT ck_postings_currency CHECK (currency LIKE N'[A-Z][A-Z][A-Z]'),
    CONSTRAINT ck_postings_side CHECK (side IN ('DEBIT', 'CREDIT') AND DATALENGTH(side) = LEN(side)),
    CONSTRAINT ck_postings_amount CHECK (amount > 0)
);

-- Supports account references and reconciliation; other indexes above enforce identity/currency keys.
CREATE INDEX ix_postings_account_currency ON dbo.postings (account_id, currency);
