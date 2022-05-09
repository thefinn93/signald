CREATE TABLE profile_keys (
    account_uuid             TEXT       NOT NULL    REFERENCES accounts(uuid) ON DELETE CASCADE,
    recipient                NUMBER     NOT NULL    REFERENCES recipients(rowid) ON DELETE CASCADE,
    profile_key              BLOB       DEFAULT NULL,
    profile_key_credential   BLOB       DEFAULT NULL,
    request_pending          boolean    DEFAULT FALSE,
    unidentified_access_mode int        DEFAULT 0,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE profiles (
    account_uuid             TEXT       NOT NULL    REFERENCES accounts(uuid) ON DELETE CASCADE,
    recipient                NUMBER     NOT NULL    REFERENCES recipients(rowid) ON DELETE CASCADE,
    last_update              NUMBER,
    given_name               TEXT,
    family_name              TEXT,
    about                    TEXT,
    emoji                    TEXT,
    payment_address          BLOB,
    badges                   TEXT,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE profile_capabilities (
    account_uuid        TEXT       NOT NULL    REFERENCES accounts(uuid) ON DELETE CASCADE,
    recipient           NUMBER     NOT NULL    REFERENCES recipients(rowid) ON DELETE CASCADE,
    storage             BOOLEAN,
    gv1_migration       BOOLEAN,
    sender_key          BOOLEAN,
    announcement_group  BOOLEAN,
    change_number       BOOLEAN,
    stories             BOOLEAN,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE profile_badges (
    account_uuid  TEXT       NOT NULL    REFERENCES accounts(uuid) ON DELETE CASCADE,
    id            TEXT       NOT NULL,
    category      TEXT       NOT NULL,
    name          TEXT       NOT NULL,
    description   TEXT       NOT NULL,
    sprite6       TEXT       NOT NULL,

    PRIMARY KEY (account_uuid, id)
);

-- remove legacy filename column from accounts column
CREATE TABLE accounts_new (
    uuid TEXT UNIQUE PRIMARY KEY NOT NULL,
    e164 TEXT UNIQUE,
    server TEXT REFERENCES servers(server_uuid)
);
INSERT INTO accounts_new SELECT uuid,e164,server FROM accounts;
DROP TABLE accounts;
ALTER TABLE accounts_new RENAME TO accounts;