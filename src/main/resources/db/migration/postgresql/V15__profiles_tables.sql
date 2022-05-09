CREATE TABLE signald_profile_keys (
    account_uuid             UUID       NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient                INTEGER    NOT NULL    REFERENCES signald_recipients(rowid) ON DELETE CASCADE,
    profile_key              BYTEA      DEFAULT NULL,
    profile_key_credential   BYTEA      DEFAULT NULL,
    request_pending          boolean    DEFAULT FALSE,
    unidentified_access_mode int        DEFAULT 0,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE signald_profiles (
    account_uuid             UUID       NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient                INTEGER    NOT NULL    REFERENCES signald_recipients(rowid) ON DELETE CASCADE,
    last_update              BIGINT,
    given_name               TEXT,
    family_name              TEXT,
    about                    TEXT,
    emoji                    TEXT,
    payment_address          BYTEA,
    badges                   TEXT,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE signald_profile_capabilities (
    account_uuid        UUID       NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient           INTEGER    NOT NULL    REFERENCES signald_recipients(rowid) ON DELETE CASCADE,
    storage             BOOLEAN,
    gv1_migration       BOOLEAN,
    sender_key          BOOLEAN,
    announcement_group  BOOLEAN,
    change_number       BOOLEAN,
    stories             BOOLEAN,

    PRIMARY KEY (account_uuid, recipient)
);

CREATE TABLE signald_profile_badges (
    account_uuid  UUID       NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    id            TEXT       NOT NULL,
    category      TEXT       NOT NULL,
    name          TEXT       NOT NULL,
    description   TEXT       NOT NULL,
    sprite6       TEXT       NOT NULL,

    PRIMARY KEY (account_uuid, id)
);

ALTER TABLE signald_accounts DROP COLUMN filename;
ALTER TABLE signald_accounts ADD PRIMARY KEY (uuid, e164, server);