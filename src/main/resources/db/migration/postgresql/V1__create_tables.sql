CREATE TABLE signald_message_queue (
    id                          SERIAL  PRIMARY KEY,
    account                     UUID    NOT NULL,
    version                     INTEGER NOT NULL,
    type                        INTEGER NOT NULL,
    source_e164                 TEXT,
    source_uuid                 UUID,
    source_device               INTEGER,
    "timestamp"                 BIGINT,
    content                     BYTEA,
    legacy_message              BYTEA,
    server_received_timestamp   BIGINT,
    server_delivered_timestamp  BIGINT,
    server_uuid                 UUID
);

CREATE TABLE signald_servers (
    server_uuid                 UUID            PRIMARY KEY,
    service_url                 TEXT            NOT NULL,
    cdn_urls                    TEXT            NOT NULL,
    contact_discovery_url       TEXT            NOT NULL,
    key_backup_url              TEXT            NOT NULL,
    storage_url                 TEXT            NOT NULL,
    zk_group_public_params      BYTEA           NOT NULL,
    unidentified_sender_root    BYTEA           NOT NULL,
    proxy                       TEXT,
    ca                          BYTEA           NOT NULL,
    key_backup_service_name     VARCHAR(64),
    key_backup_service_id       VARCHAR(64),
    key_backup_mrenclave        VARCHAR(64),
    cds_mrenclave               VARCHAR(64),
    ias_ca                      BYTEA
);

INSERT INTO signald_servers VALUES
    (
        '6e2eb5a8-5706-45d0-8377-127a816411a4', -- server_uuid
        'https://chat.signal.org', -- service_url
        '{"0":"https://cdn.signal.org","2":"https://cdn2.signal.org"}', -- cdn_urls
        'https://api.directory.signal.org', -- contact_discovery_url
        'https://api.backup.signal.org', -- key_backup_url
        'https://storage.signal.org', -- storage_url
        -- zk_group_public_params
        E'\\x00c85fe72c15c084d932c7dffde0b2b9d671f490e692491b57a3c89f31b8a7cc7756a54948588cbe7be510a5ae4686ffd5e6887ad477d4861e01b9b435d3ae1c7f108be45ec62d702e5a73228d60b2d1d605f673cb5faa1d15790384ea3e9d7963304f9b45928205ba3db4a7f85e257f9ed50a71c5ee9f12bf3000d996493d825446df17edb6e0f87de2f8f1231fd0d722d344aacdac35cba0dfbc594032e6ed7dfa9cea063ece785ec106ccf74457e8ad40d1941448d8e97f54bfe01cba4b3b369c86bc2a0ac46c202a01395f227e9cd2a5c871ce2dbe8dd4db87c81ad9ae0b58fc96091d1a28a39084a98281a9d16799b4d5184902bc92b12e78f02967fe7c43e859e4058f939b0e370a3197f6266d807baf71fa2914e60057b119a817de065d',
        -- unidentified_sender_root
        E'\\x057bba408295cf9300f20b2dcdf3a04501aac8ba8ec0d2872fa20d92fdc81d6305',
        -- proxy
        NULL,
        -- ca
        E'\\x0000000100000014e119602f608a01dba6f603f0212fa3a85bbe307c000007b7010011746578747365637572652d67636d2d63610000013da3b16fa9000000000005582e353039000003f3308203ef308202d7a00302010202090089ba2dab4ae4f362300d06092a864886f70d010105050030818d310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d53616e204672616e636973636f311d301b060355040a0c144f70656e20576869737065722053797374656d73311d301b060355040b0c144f70656e20576869737065722053797374656d733113301106035504030c0a54657874536563757265301e170d3133303332353232313833355a170d3233303332333232313833355a30818d310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d53616e204672616e636973636f311d301b060355040a0c144f70656e20576869737065722053797374656d73311d301b060355040b0c144f70656e20576869737065722053797374656d733113301106035504030c0a5465787453656375726530820122300d06092a864886f70d01010105000382010f003082010a0282010100c14960693820431748b8ab67788c05e4497506a5b796ba054f4028da2faa83f500cec5e5b569fbdf3e8f3ade2e82aead4a0f11e9077e84c3355c8f02e415320c7a8eedf6cd45428940b77984ad5d94d2f89ee957acfb28fa82b49fc7020301c76c2a62346b549009996bdd0d6d70702400a151c5835ac000f2a6ce41f999aa7207fae774d74b9ff420f3d02e870b9fc1962f039836761f06f20b283308b66653b446ee71d8a0c4403d99f22fbc700311bb5e2ed560c019bd479bd5bedc2b0fa082c48de9807ec41fb90c3f439df77c0b8acbe7d7284763b16e625ae462a76432f594acaa00495c388dbebdbda789094754f870a85ce48fd4f41c2fa59e9cd0870203010001a350304e301d0603551d0e04160414018b18f13ffb3919446e8586be946532a7323c90301f0603551d23041830168014018b18f13ffb3919446e8586be946532a7323c90300c0603551d13040530030101ff300d06092a864886f70d010105050003820101007e1ebe210b9ea6d0bbc902ed512ba36e3c36d330437bfe33288c51f11f83f75cf15fba98d1f9b903ab4a4f394e898c8c67ac3d570ff1159934da4d563988e85ecafed4cc939e7dbda92ce73daacbaf2387e76844a1f5b873967f8b5f1b42ad13c6864c40d48308a3727fb36585fca558672722bba6f8b9b27dd573c0b81359c089ae83bfecad6bdba64d21aba03acf5e345935522ad246ec43f8d6425efe8178bac63927b2cf9312b72d6ccc2676f9108243991bf21fca724705a0e909baaa2185688e272fc414e0b6b5c767a7e0319745a2cc3d1c2310b36040fa1d477f33a0232e03c80765f65862867b0fb435c0d522547731b85ac478169825b2c4af02d500bbf2ff8236b854e90220d0ed6cfbc9a370eed025',
        -- key_backup_service_name
        'fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe',
        -- key_backup_service_id
        'fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe',
        -- key_backup_mrenclave
        'a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87',
        -- cds_mrenclave
        'c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15',
        -- ias_ca
        E'\\x00000002000000141e139877c131235200356d48d741b9e8538d4a290000078101000369617300000164adeb7976000000000005582e3530390000054f3082054b308203b3a003020102020900d107765d32a3b094300d06092a864886f70d01010b0500307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e672043413020170d3136313131343135333733315a180f32303439313233313233353935395a307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e67204341308201a2300d06092a864886f70d01010105000382018f003082018a02820181009f3c647eb5773cbb512d2732c0d7415ebb55a0fa9ede2e649199e6821db910d53177370977466a6a5e4786ccd2ddebd4149d6a2f6325529dd10cc98737b0779c1a07e29c47a1ae004948476c489f45a5a15d7ac8ecc6acc645adb43d87679df59c093bc5a2e9696c5478541b979e754b573914be55d32ff4c09ddf27219934cd990527b3f92ed78fbf29246abecb71240ef39c2d7107b447545a7ffb10eb060a68a98580219e36910952683892d6a5e2a80803193e407531404e36b315623799aa825074409754a2dfe8f5afd5fe631e1fc2af3808906f28a790d9dd9fe060939b125790c5805d037df56a99531b96de69de33ed226cc1207d1042b5c9ab7f404fc711c0fe4769fb9578b1dc0ec469ea1a25e0ff9914886ef2699b235bb4847dd6ff40b606e6170793c2fb98b314587f9cfd257362dfeab10b3bd2d97673a1a4bd44c453aaf47fc1f2d3d0f384f74a06f89c089f0da6cdb7fceee8c9821a8e54f25c0416d18c46839a5f8012fbdd3dc74d256279adc2c0d55aff6f0622425d1b0203010001a381c93081c630600603551d1f045930573055a053a051864f687474703a2f2f7472757374656473657276696365732e696e74656c2e636f6d2f636f6e74656e742f43524c2f5347582f4174746573746174696f6e5265706f72745369676e696e6743412e63726c301d0603551d0e0416041478437b76a67ebcd0af7e4237eb357c3b8701513c301f0603551d2304183016801478437b76a67ebcd0af7e4237eb357c3b8701513c300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100300d06092a864886f70d01010b05000382018100785f2d60c5c80af42a797610213915da82c9b29e89e0902a25a6c75b16091c68ab204aae711889492c7e1e320911455a8fc13442312e77a63994d99795c8ea4576823cea8ad1e191cfa862fab8a932d3d9b0535a0702d0555f74e520e30330f33480e7adc9d7c81e20703142bf00c528a80b463381fd602a82c7035281aae59562ccb5334ea8903e650b010681f5ce8eb62eac9c414988243aec92f25bf13cdff7ebcc298ee51bba5a3538b66b26cbc45a51de003cad306531ad7cf5d4ef0f8805d1b9133d24135ab3c4641a2f8808349d7333295e0e76ee4bc5227232628efa80d79d92ab4e3d1120f3fb5ad119cd8d544aa1d4a6865e6b57beac5771307e2e3cb9070da47b4bfc8869e01413ea093541de8a792811b74636c5e91452cf0cee59f2fb404acd0bc584cb9c835404734c0e7ec6605cdfcf2ff439b6d4719f702f0e0c3fa04fdb12a6cb2ad1ab1c9af1f8f4c3a08edd72a32b0bb5d0ad256ffd159a683b2a5a1f1d11fa62532f03d754caef0da5735a1e5a884c7e89d91218c9d7008515e5f5992ccc471f3b1bc1aaec24a2997e6ad3'
    ),
    (
        '97c17f0c-e53b-426f-8ffa-c052d4183f83', -- server_uuid
        'https://chat.staging.signal.org', -- service_url
        '{"0":"https://cdn-staging.signal.org","2":"https://cdn2-staging.signal.org"}', -- cdn_urls
        'https://api-staging.directory.signal.org', -- contact_discovery_url
        'https://api-staging.backup.signal.org', -- key_backup_url
        'https://storage-staging.signal.org', -- storage_url
        -- zk_group_public_params
        E'\\x001498db555c91071b49754d08645825c7d61e200c666a53b5310b7039b181d15bb69fdb5ac4b165d30acdf0a9f2bbc8b3ca1c094dc1dfb7d3debe0c8b9a807a6786791d97fbf626386479a1fba2eed0f998341fb2d008f62fb85a932d21ef0a0b7c14e70dc89eadee356566a06b692a776c35fc09ac28341ddf7398e6e1ca95274a47d89f6a2830e3a70697dd6a746daef7ad6546b20cc482e624917172a9765ba4ae9cf3b0222f1308f042525854f3903e3e15d05e145d705d1d22cad39ba83c10901bc1bdad820679d62c0a52579dbae01981b778c4c6e619f1e17e27b404418042ee3165941047d22b49a35e0fbfda53e659c4d9591f6792a81040fd2d6f3ba23e6ef81f6c0c3b8bb559a7def94c32225213f4beca2d2d7d030f2be2c3eb5d',
        -- unidentified_sender_root
        E'\\x05ba98d43ce8844e0d519a1517e2f5f2850facade420b9652c4261d949cf4ac131',
        -- proxy
        NULL,
        -- ca
        E'\\x0000000100000014e119602f608a01dba6f603f0212fa3a85bbe307c000007b7010011746578747365637572652d67636d2d63610000013da3b16fa9000000000005582e353039000003f3308203ef308202d7a00302010202090089ba2dab4ae4f362300d06092a864886f70d010105050030818d310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d53616e204672616e636973636f311d301b060355040a0c144f70656e20576869737065722053797374656d73311d301b060355040b0c144f70656e20576869737065722053797374656d733113301106035504030c0a54657874536563757265301e170d3133303332353232313833355a170d3233303332333232313833355a30818d310b30090603550406130255533113301106035504080c0a43616c69666f726e69613116301406035504070c0d53616e204672616e636973636f311d301b060355040a0c144f70656e20576869737065722053797374656d73311d301b060355040b0c144f70656e20576869737065722053797374656d733113301106035504030c0a5465787453656375726530820122300d06092a864886f70d01010105000382010f003082010a0282010100c14960693820431748b8ab67788c05e4497506a5b796ba054f4028da2faa83f500cec5e5b569fbdf3e8f3ade2e82aead4a0f11e9077e84c3355c8f02e415320c7a8eedf6cd45428940b77984ad5d94d2f89ee957acfb28fa82b49fc7020301c76c2a62346b549009996bdd0d6d70702400a151c5835ac000f2a6ce41f999aa7207fae774d74b9ff420f3d02e870b9fc1962f039836761f06f20b283308b66653b446ee71d8a0c4403d99f22fbc700311bb5e2ed560c019bd479bd5bedc2b0fa082c48de9807ec41fb90c3f439df77c0b8acbe7d7284763b16e625ae462a76432f594acaa00495c388dbebdbda789094754f870a85ce48fd4f41c2fa59e9cd0870203010001a350304e301d0603551d0e04160414018b18f13ffb3919446e8586be946532a7323c90301f0603551d23041830168014018b18f13ffb3919446e8586be946532a7323c90300c0603551d13040530030101ff300d06092a864886f70d010105050003820101007e1ebe210b9ea6d0bbc902ed512ba36e3c36d330437bfe33288c51f11f83f75cf15fba98d1f9b903ab4a4f394e898c8c67ac3d570ff1159934da4d563988e85ecafed4cc939e7dbda92ce73daacbaf2387e76844a1f5b873967f8b5f1b42ad13c6864c40d48308a3727fb36585fca558672722bba6f8b9b27dd573c0b81359c089ae83bfecad6bdba64d21aba03acf5e345935522ad246ec43f8d6425efe8178bac63927b2cf9312b72d6ccc2676f9108243991bf21fca724705a0e909baaa2185688e272fc414e0b6b5c767a7e0319745a2cc3d1c2310b36040fa1d477f33a0232e03c80765f65862867b0fb435c0d522547731b85ac478169825b2c4af02d500bbf2ff8236b854e90220d0ed6cfbc9a370eed025',
        -- key_backup_service_name
        '823a3b2c037ff0cbe305cc48928cfcc97c9ed4a8ca6d49af6f7d6981fb60a4e9',
        -- key_backup_service_id
        '16b94ac6d2b7f7b9d72928f36d798dbb35ed32e7bb14c42b4301ad0344b46f29',
        -- key_backup_mrenclave
        'a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87',
        -- cds_mrenclave
        'c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15',
        -- ias_ca
        E'\\x00000002000000141e139877c131235200356d48d741b9e8538d4a290000078101000369617300000164adeb7976000000000005582e3530390000054f3082054b308203b3a003020102020900d107765d32a3b094300d06092a864886f70d01010b0500307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e672043413020170d3136313131343135333733315a180f32303439313233313233353935395a307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e67204341308201a2300d06092a864886f70d01010105000382018f003082018a02820181009f3c647eb5773cbb512d2732c0d7415ebb55a0fa9ede2e649199e6821db910d53177370977466a6a5e4786ccd2ddebd4149d6a2f6325529dd10cc98737b0779c1a07e29c47a1ae004948476c489f45a5a15d7ac8ecc6acc645adb43d87679df59c093bc5a2e9696c5478541b979e754b573914be55d32ff4c09ddf27219934cd990527b3f92ed78fbf29246abecb71240ef39c2d7107b447545a7ffb10eb060a68a98580219e36910952683892d6a5e2a80803193e407531404e36b315623799aa825074409754a2dfe8f5afd5fe631e1fc2af3808906f28a790d9dd9fe060939b125790c5805d037df56a99531b96de69de33ed226cc1207d1042b5c9ab7f404fc711c0fe4769fb9578b1dc0ec469ea1a25e0ff9914886ef2699b235bb4847dd6ff40b606e6170793c2fb98b314587f9cfd257362dfeab10b3bd2d97673a1a4bd44c453aaf47fc1f2d3d0f384f74a06f89c089f0da6cdb7fceee8c9821a8e54f25c0416d18c46839a5f8012fbdd3dc74d256279adc2c0d55aff6f0622425d1b0203010001a381c93081c630600603551d1f045930573055a053a051864f687474703a2f2f7472757374656473657276696365732e696e74656c2e636f6d2f636f6e74656e742f43524c2f5347582f4174746573746174696f6e5265706f72745369676e696e6743412e63726c301d0603551d0e0416041478437b76a67ebcd0af7e4237eb357c3b8701513c301f0603551d2304183016801478437b76a67ebcd0af7e4237eb357c3b8701513c300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100300d06092a864886f70d01010b05000382018100785f2d60c5c80af42a797610213915da82c9b29e89e0902a25a6c75b16091c68ab204aae711889492c7e1e320911455a8fc13442312e77a63994d99795c8ea4576823cea8ad1e191cfa862fab8a932d3d9b0535a0702d0555f74e520e30330f33480e7adc9d7c81e20703142bf00c528a80b463381fd602a82c7035281aae59562ccb5334ea8903e650b010681f5ce8eb62eac9c414988243aec92f25bf13cdff7ebcc298ee51bba5a3538b66b26cbc45a51de003cad306531ad7cf5d4ef0f8805d1b9133d24135ab3c4641a2f8808349d7333295e0e76ee4bc5227232628efa80d79d92ab4e3d1120f3fb5ad119cd8d544aa1d4a6865e6b57beac5771307e2e3cb9070da47b4bfc8869e01413ea093541de8a792811b74636c5e91452cf0cee59f2fb404acd0bc584cb9c835404734c0e7ec6605cdfcf2ff439b6d4719f702f0e0c3fa04fdb12a6cb2ad1ab1c9af1f8f4c3a08edd72a32b0bb5d0ad256ffd159a683b2a5a1f1d11fa62532f03d754caef0da5735a1e5a884c7e89d91218c9d7008515e5f5992ccc471f3b1bc1aaec24a2997e6ad3'
    );

CREATE TABLE signald_accounts (
    uuid        UUID    NOT NULL,
    e164        TEXT    NOT NULL,
    filename    TEXT    NOT NULL,
    server      UUID    NOT NULL    REFERENCES signald_servers(server_uuid) ON DELETE CASCADE,

    PRIMARY KEY (uuid, e164, filename, server),
    UNIQUE      (e164),
    UNIQUE      (filename),
    UNIQUE      (uuid)
);

CREATE TABLE signald_recipients (
    rowid           SERIAL  PRIMARY KEY,
    account_uuid    UUID    NOT NULL        REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    uuid            UUID,
    e164            TEXT,

    UNIQUE (account_uuid, e164, uuid)
);

CREATE TABLE signald_prekeys (
    account_uuid    UUID    NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    id              INTEGER NOT NULL,
    record          BYTEA   NOT NULL,

    PRIMARY KEY (account_uuid, id)
);

CREATE TABLE signald_sessions (
    account_uuid    UUID        NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient       INTEGER     NOT NULL    REFERENCES signald_recipients(rowid) ON DELETE CASCADE,
    device_id       INTEGER,
    record          BYTEA       NOT NULL,

    PRIMARY KEY (account_uuid, recipient, device_id)
);

CREATE TABLE signald_signed_prekeys (
    account_uuid    UUID    NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    id              INTEGER NOT NULL,
    record          BYTEA   NOT NULL,

    PRIMARY KEY (account_uuid, id)
);

CREATE TABLE signald_identity_keys (
    account_uuid    UUID                            NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    recipient       INTEGER                         NOT NULL,
    identity_key    BYTEA                           NOT NULL,
    trust_level     TEXT                            NOT NULL,
    added           TIMESTAMP WITHOUT TIME ZONE                 DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (account_uuid, recipient, identity_key)
);

CREATE TABLE signald_account_data (
    account_uuid    UUID    NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    key             TEXT    NOT NULL,
    value           BYTEA   NOT NULL,

    PRIMARY KEY (account_uuid, key)
);

CREATE TABLE signald_pending_account_data (
    username    TEXT,
    key         TEXT    NOT NULL,
    value       BYTEA   NOT NULL,

    PRIMARY KEY (username, key)
);

CREATE TABLE signald_sender_keys (
    account_uuid    UUID                            NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    address         TEXT                            NOT NULL,
    device          INTEGER                         NOT NULL,
    distribution_id UUID                            NOT NULL,
    record          BYTEA                           NOT NULL,
    created_at      TIMESTAMP WITHOUT TIME ZONE     NOT NULL,

    PRIMARY KEY (account_uuid, address, device, distribution_id)
);

CREATE TABLE signald_sender_key_shared (
    account_uuid    UUID    NOT NULL    REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    distribution_id UUID    NOT NULL,
    address         TEXT    NOT NULL,
    device          INTEGER NOT NULL,

    PRIMARY KEY (account_uuid, address, device)
);

CREATE TABLE signald_groups (
    rowid               SERIAL  PRIMARY KEY,
    account_uuid        UUID    NOT NULL        REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    group_id            BYTEA   NOT NULL,
    master_key          BYTEA   NOT NULL,
    revision            INTEGER NOT NULL,
    last_avatar_fetch   INTEGER,
    distribution_id     UUID,
    group_info          BYTEA,

    UNIQUE (account_uuid, group_id)
);

CREATE TABLE signald_group_credentials (
    account_uuid    UUID    NOT NULL REFERENCES signald_accounts(uuid) ON DELETE CASCADE,
    date            BIGINT  NOT NULL,
    credential      BYTEA   NOT NULL,

    PRIMARY KEY (account_uuid, date)
);