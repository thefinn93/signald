CREATE TABLE identity_keys_new (
    account_uuid TEXT NOT NULL,
    recipient INTEGER NOT NULL,
    identity_key BLOB NOT NULL,
    trust_level TEXT NOT NULL,
    added DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid,recipient,identity_key)
);
INSERT INTO identity_keys_new SELECT * FROM identity_keys GROUP BY account_uuid,recipient,identity_key ORDER BY added ASC;
DROP TABLE identity_keys;
ALTER TABLE identity_keys_new RENAME TO identity_keys;