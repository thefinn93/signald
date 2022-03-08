DROP TABLE sender_keys; -- previous table and was never used and tracked recipient instead of address. Signal Android tracks address string
CREATE TABLE sender_keys(
    account_uuid TEXT NOT NULL,
    address TEXT NOT NULL,
    device INTEGER NOT NULL,
    distribution_id TEXT NOT NULL,
    record BLOB NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(account_uuid) REFERENCES accounts(uuid),
    UNIQUE(account_uuid,address,device,distribution_id) ON CONFLICT REPLACE
);