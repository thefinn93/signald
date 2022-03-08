CREATE TABLE sender_keys (
    account_uuid TEXT NOT NULL,
    recipient INTEGER NOT NULL,
    device INTEGER NOT NULL,
    distribution_id TEXT NOT NULL,
    record BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid,recipient,device,distribution_id) ON CONFLICT REPLACE
);

CREATE TABLE sender_key_shared (
    account_uuid TEXT NOT NULL,
    distribution_id TEXT NOT NULL,
    address TEXT NOT NULL,
    device INTEGER NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid,distribution_id,address,device) ON CONFLICT REPLACE
);