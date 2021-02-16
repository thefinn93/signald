CREATE TABLE message_queue (
    id INTEGER PRIMARY KEY,
    account TEXT NOT NULL,
    version INTEGER NOT NULL,
    type INTEGER NOT NULL,
    source_e164 TEXT,
    source_uuid TEXT,
    source_device INTEGER,
    timestamp INTEGER,
    content BLOB,
    legacy_message BLOB,
    server_received_timestamp INTEGER,
    server_delivered_timestamp INTEGER,
    server_uuid TEXT
);