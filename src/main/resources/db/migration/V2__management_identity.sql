CREATE TABLE management_account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL CHECK (role IN ('viewer', 'editor', 'admin')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    password_hash TEXT,
    credential_version INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE management_bootstrap (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    state TEXT NOT NULL CHECK (state IN ('unreserved', 'reserved', 'activated')),
    account_id INTEGER UNIQUE REFERENCES management_account(id)
);
INSERT INTO management_bootstrap(singleton, state) VALUES (1, 'unreserved');

CREATE TABLE management_token (
    digest TEXT PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES management_account(id) ON DELETE CASCADE,
    purpose TEXT NOT NULL CHECK (purpose IN ('set', 'reset')),
    expires_at INTEGER NOT NULL,
    consumed_at INTEGER
);
CREATE INDEX management_token_account ON management_token(account_id, purpose, consumed_at);
