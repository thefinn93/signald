CREATE TABLE accounts (
    uuid TEXT UNIQUE NOT NULL,
    e164 TEXT UNIQUE,
    filename TEXT UNIQUE NOT NULL
);

CREATE TABLE recipients (
    account_uuid TEXT NOT NULL,
    uuid TEXT,
    e164 TEXT,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid, uuid),
    UNIQUE(account_uuid, e164)
);

CREATE TABLE prekeys (
    account_uuid TEXT NOT NULL,
    id INTEGER NOT NULL,
    record BLOB NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid, id)
);

CREATE TABLE sessions (
    account_uuid TEXT NOT NULL,
    recipient INTEGER NOT NULL,
    device_id INTEGER,
    record BLOB NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    FOREIGN KEY(recipient) REFERENCES recipients(rowid),
    UNIQUE(account_uuid,recipient,device_id)
);

CREATE TABLE signed_prekeys (
    account_uuid TEXT NOT NULL,
    id INTEGER NOT NULL,
    record BLOB NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid, id)
);

CREATE TABLE identity_keys (
    account_uuid TEXT NOT NULL,
    recipient INTEGER NOT NULL,
    identity_key BLOB NOT NULL,
    trust_level TEXT NOT NULL,
    added DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid)
);

CREATE TABLE account_data (
    account_uuid TEXT NOT NULL,
    key TEXT NOT NULL,
    value BLOB NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid, key)
);

CREATE TABLE pending_account_data (
    username TEXT,
    key TEXT NOT NULL,
    value BLOB NOT NULL,
    UNIQUE(username, key)
);