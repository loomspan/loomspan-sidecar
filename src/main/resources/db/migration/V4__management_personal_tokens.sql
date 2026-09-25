CREATE TABLE management_personal_token (
    id TEXT PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES management_account(id) ON DELETE CASCADE,
    secret_digest TEXT NOT NULL,
    preset TEXT NOT NULL CHECK (preset IN ('read', 'edit', 'publish')),
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL CHECK (expires_at > created_at),
    revoked_at INTEGER
);
CREATE INDEX management_personal_token_owner ON management_personal_token(account_id, created_at);
