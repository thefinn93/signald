CREATE TABLE contacts (
    account_uuid            TEXT        NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient               INTEGER     NOT NULL    REFERENCES signald_recipients(rowid) ON DELETE CASCADE,
    name                    TEXT,
    color                   TEXT,
    profile_key             BLOB,
    message_expiration_time INTEGER,
    inbox_position          INTEGER,

    PRIMARY KEY (account_uuid, recipient)
);
