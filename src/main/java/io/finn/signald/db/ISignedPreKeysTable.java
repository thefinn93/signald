package io.finn.signald.db;

import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

public interface ISignedPreKeysTable extends SignedPreKeyStore {
  String ACCOUNT_UUID = "account_uuid";
  String ID = "id";
  String RECORD = "record";

  void deleteAccount(UUID uuid) throws SQLException;
}
