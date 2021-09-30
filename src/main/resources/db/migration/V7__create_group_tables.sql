CREATE TABLE groups (
    account_uuid TEXT NOT NULL,
    group_id BLOB NOT NULL,
    master_key BLOB NOT NULL,
    revision INT NOT NULL,
    last_avatar_fetch INT,
    distribution_id TEXT,
    group_info BLOB,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid,group_id)
);

CREATE TABLE group_credentials (
    account_uuid TEXT NOT NULL,
    date INT NOT NULL,
    credential BLOB NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid)
);