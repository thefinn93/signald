ALTER TABLE servers ADD COLUMN key_backup_service_name VARCHAR(64);
ALTER TABLE servers ADD COLUMN key_backup_service_id VARCHAR(64);
ALTER TABLE servers ADD COLUMN key_backup_mrenclave VARCHAR(64);
ALTER TABLE servers ADD COLUMN cds_mrenclave VARCHAR(64);
ALTER TABLE servers ADD COLUMN ias_ca BLOB;

--- add new values to existing rows, and update the service and contact discovery URLs
UPDATE servers SET
    service_url = 'https://chat.signal.org',
    contact_discovery_url = 'https://api.directory.signal.org',
    key_backup_service_name = 'fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe',
    key_backup_service_id = 'fe7c1bfae98f9b073d220366ea31163ee82f6d04bead774f71ca8e5c40847bfe',
    key_backup_mrenclave = 'a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87',
    cds_mrenclave = 'c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15',
    ias_ca = X'00000002000000141e139877c131235200356d48d741b9e8538d4a290000078101000369617300000164adeb7976000000000005582e3530390000054f3082054b308203b3a003020102020900d107765d32a3b094300d06092a864886f70d01010b0500307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e672043413020170d3136313131343135333733315a180f32303439313233313233353935395a307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e67204341308201a2300d06092a864886f70d01010105000382018f003082018a02820181009f3c647eb5773cbb512d2732c0d7415ebb55a0fa9ede2e649199e6821db910d53177370977466a6a5e4786ccd2ddebd4149d6a2f6325529dd10cc98737b0779c1a07e29c47a1ae004948476c489f45a5a15d7ac8ecc6acc645adb43d87679df59c093bc5a2e9696c5478541b979e754b573914be55d32ff4c09ddf27219934cd990527b3f92ed78fbf29246abecb71240ef39c2d7107b447545a7ffb10eb060a68a98580219e36910952683892d6a5e2a80803193e407531404e36b315623799aa825074409754a2dfe8f5afd5fe631e1fc2af3808906f28a790d9dd9fe060939b125790c5805d037df56a99531b96de69de33ed226cc1207d1042b5c9ab7f404fc711c0fe4769fb9578b1dc0ec469ea1a25e0ff9914886ef2699b235bb4847dd6ff40b606e6170793c2fb98b314587f9cfd257362dfeab10b3bd2d97673a1a4bd44c453aaf47fc1f2d3d0f384f74a06f89c089f0da6cdb7fceee8c9821a8e54f25c0416d18c46839a5f8012fbdd3dc74d256279adc2c0d55aff6f0622425d1b0203010001a381c93081c630600603551d1f045930573055a053a051864f687474703a2f2f7472757374656473657276696365732e696e74656c2e636f6d2f636f6e74656e742f43524c2f5347582f4174746573746174696f6e5265706f72745369676e696e6743412e63726c301d0603551d0e0416041478437b76a67ebcd0af7e4237eb357c3b8701513c301f0603551d2304183016801478437b76a67ebcd0af7e4237eb357c3b8701513c300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100300d06092a864886f70d01010b05000382018100785f2d60c5c80af42a797610213915da82c9b29e89e0902a25a6c75b16091c68ab204aae711889492c7e1e320911455a8fc13442312e77a63994d99795c8ea4576823cea8ad1e191cfa862fab8a932d3d9b0535a0702d0555f74e520e30330f33480e7adc9d7c81e20703142bf00c528a80b463381fd602a82c7035281aae59562ccb5334ea8903e650b010681f5ce8eb62eac9c414988243aec92f25bf13cdff7ebcc298ee51bba5a3538b66b26cbc45a51de003cad306531ad7cf5d4ef0f8805d1b9133d24135ab3c4641a2f8808349d7333295e0e76ee4bc5227232628efa80d79d92ab4e3d1120f3fb5ad119cd8d544aa1d4a6865e6b57beac5771307e2e3cb9070da47b4bfc8869e01413ea093541de8a792811b74636c5e91452cf0cee59f2fb404acd0bc584cb9c835404734c0e7ec6605cdfcf2ff439b6d4719f702f0e0c3fa04fdb12a6cb2ad1ab1c9af1f8f4c3a08edd72a32b0bb5d0ad256ffd159a683b2a5a1f1d11fa62532f03d754caef0da5735a1e5a884c7e89d91218c9d7008515e5f5992ccc471f3b1bc1aaec24a2997e6ad3'
    WHERE server_uuid = '6e2eb5a8-5706-45d0-8377-127a816411a4';

UPDATE servers SET
    service_url = 'https://chat.staging.signal.org',
    contact_discovery_url = 'https://api-staging.directory.signal.org',
    key_backup_url = 'https://api-staging.backup.signal.org',
    key_backup_service_name = '823a3b2c037ff0cbe305cc48928cfcc97c9ed4a8ca6d49af6f7d6981fb60a4e9',
    key_backup_service_id = '16b94ac6d2b7f7b9d72928f36d798dbb35ed32e7bb14c42b4301ad0344b46f29',
    key_backup_mrenclave = 'a3baab19ef6ce6f34ab9ebb25ba722725ae44a8872dc0ff08ad6d83a9489de87',
    cds_mrenclave = 'c98e00a4e3ff977a56afefe7362a27e4961e4f19e211febfbb19b897e6b80b15',
    ias_ca = X'00000002000000141e139877c131235200356d48d741b9e8538d4a290000078101000369617300000164adeb7976000000000005582e3530390000054f3082054b308203b3a003020102020900d107765d32a3b094300d06092a864886f70d01010b0500307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e672043413020170d3136313131343135333733315a180f32303439313233313233353935395a307e310b3009060355040613025553310b300906035504080c0243413114301206035504070c0b53616e746120436c617261311a3018060355040a0c11496e74656c20436f72706f726174696f6e3130302e06035504030c27496e74656c20534758204174746573746174696f6e205265706f7274205369676e696e67204341308201a2300d06092a864886f70d01010105000382018f003082018a02820181009f3c647eb5773cbb512d2732c0d7415ebb55a0fa9ede2e649199e6821db910d53177370977466a6a5e4786ccd2ddebd4149d6a2f6325529dd10cc98737b0779c1a07e29c47a1ae004948476c489f45a5a15d7ac8ecc6acc645adb43d87679df59c093bc5a2e9696c5478541b979e754b573914be55d32ff4c09ddf27219934cd990527b3f92ed78fbf29246abecb71240ef39c2d7107b447545a7ffb10eb060a68a98580219e36910952683892d6a5e2a80803193e407531404e36b315623799aa825074409754a2dfe8f5afd5fe631e1fc2af3808906f28a790d9dd9fe060939b125790c5805d037df56a99531b96de69de33ed226cc1207d1042b5c9ab7f404fc711c0fe4769fb9578b1dc0ec469ea1a25e0ff9914886ef2699b235bb4847dd6ff40b606e6170793c2fb98b314587f9cfd257362dfeab10b3bd2d97673a1a4bd44c453aaf47fc1f2d3d0f384f74a06f89c089f0da6cdb7fceee8c9821a8e54f25c0416d18c46839a5f8012fbdd3dc74d256279adc2c0d55aff6f0622425d1b0203010001a381c93081c630600603551d1f045930573055a053a051864f687474703a2f2f7472757374656473657276696365732e696e74656c2e636f6d2f636f6e74656e742f43524c2f5347582f4174746573746174696f6e5265706f72745369676e696e6743412e63726c301d0603551d0e0416041478437b76a67ebcd0af7e4237eb357c3b8701513c301f0603551d2304183016801478437b76a67ebcd0af7e4237eb357c3b8701513c300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff020100300d06092a864886f70d01010b05000382018100785f2d60c5c80af42a797610213915da82c9b29e89e0902a25a6c75b16091c68ab204aae711889492c7e1e320911455a8fc13442312e77a63994d99795c8ea4576823cea8ad1e191cfa862fab8a932d3d9b0535a0702d0555f74e520e30330f33480e7adc9d7c81e20703142bf00c528a80b463381fd602a82c7035281aae59562ccb5334ea8903e650b010681f5ce8eb62eac9c414988243aec92f25bf13cdff7ebcc298ee51bba5a3538b66b26cbc45a51de003cad306531ad7cf5d4ef0f8805d1b9133d24135ab3c4641a2f8808349d7333295e0e76ee4bc5227232628efa80d79d92ab4e3d1120f3fb5ad119cd8d544aa1d4a6865e6b57beac5771307e2e3cb9070da47b4bfc8869e01413ea093541de8a792811b74636c5e91452cf0cee59f2fb404acd0bc584cb9c835404734c0e7ec6605cdfcf2ff439b6d4719f702f0e0c3fa04fdb12a6cb2ad1ab1c9af1f8f4c3a08edd72a32b0bb5d0ad256ffd159a683b2a5a1f1d11fa62532f03d754caef0da5735a1e5a884c7e89d91218c9d7008515e5f5992ccc471f3b1bc1aaec24a2997e6ad3'
    WHERE server_uuid = '97c17f0c-e53b-426f-8ffa-c052d4183f83';