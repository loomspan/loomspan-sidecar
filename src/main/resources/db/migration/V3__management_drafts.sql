CREATE TABLE management_draft (
    account_id INTEGER PRIMARY KEY REFERENCES management_account(id) ON DELETE CASCADE,
    draft_id TEXT NOT NULL UNIQUE,
    base_snapshot_id TEXT NOT NULL,
    source_snapshot_id TEXT,
    revision INTEGER NOT NULL CHECK (revision > 0),
    document_count INTEGER NOT NULL CHECK (document_count >= 0),
    rest_routes_yaml TEXT NOT NULL,
    execution_configuration_yaml TEXT NOT NULL
);

CREATE TABLE management_draft_document (
    account_id INTEGER NOT NULL REFERENCES management_draft(account_id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    label TEXT NOT NULL CHECK (length(trim(label)) > 0),
    yaml TEXT NOT NULL,
    PRIMARY KEY (account_id, ordinal),
    UNIQUE (account_id, label)
);
