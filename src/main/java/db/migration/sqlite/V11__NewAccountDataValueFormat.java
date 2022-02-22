package db.migration.sqlite;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.whispersystems.libsignal.util.Pair;

public class V11__NewAccountDataValueFormat extends BaseJavaMigration {
  private static final Logger logger = LogManager.getLogger();

  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String KEY = "key";
  private static final String VALUE = "value";

  @Override
  public void migrate(Context context) throws SQLException {
    HashMap<String, List<Pair<String, byte[]>>> migratedData = new HashMap<>();
    try (PreparedStatement statement = context.getConnection().prepareStatement("SELECT account_uuid, key, value FROM account_data")) {
      ResultSet rows = statement.executeQuery();
      while (rows.next()) {
        String account = rows.getString(ACCOUNT_UUID);
        String key = rows.getString(KEY);
        byte[] value;
        switch (key) {
        case "OWN_IDENTITY_KEY_PAIR":
        case "SENDER_CERTIFICATE":
        case "STORAGE_KEY":
          value = rows.getBytes(VALUE);
          break;
        case "LOCAL_REGISTRATION_ID":
        case "DEVICE_ID":
        case "LAST_ACCOUNT_REFRESH":
        case "PRE_KEY_ID_OFFSET":
        case "NEXT_SIGNED_PRE_KEY_ID":
        case "LAST_ACCOUNT_REPAIR":
          value = ByteBuffer.allocate(4).putInt(rows.getInt(VALUE)).array();
          break;
        case "LAST_PRE_KEY_REFRESH":
        case "SENDER_CERTIFICATE_REFRESH_TIME":
        case "STORAGE_MANIFEST_VERSION":
          value = ByteBuffer.allocate(8).putLong(rows.getLong(VALUE)).array();
          break;
        case "DEVICE_NAME":
        case "PASSWORD":
          String originalValue = rows.getString(VALUE);
          value = originalValue != null ? originalValue.getBytes() : new byte[] {};
          break;
        case "MULTI_DEVICE":
          // booleans are treated as ints with value 1 or 0
          value = ByteBuffer.allocate(4).putInt(rows.getBoolean(VALUE) ? 1 : 0).array();
          break;
        default:
          logger.warn("dropping unexpected account data account={} key={}", account, key);
          continue;
        }
        if (!migratedData.containsKey(account)) {
          migratedData.put(account, new ArrayList<>());
        }
        migratedData.get(account).add(new Pair<>(key, value));
      }
    }

    for (String account : migratedData.keySet()) {
      for (Pair<String, byte[]> entry : migratedData.get(account)) {
        try (var statement = context.getConnection().prepareStatement("UPDATE account_data SET value = ? WHERE account_uuid = ? AND key = ?")) {
          statement.setBytes(1, entry.second());
          statement.setString(2, account);
          statement.setString(3, entry.first());
          int rowCount = statement.executeUpdate();
          if (rowCount != 1) {
            logger.warn("unexpected number of rows updated for account={} key={}. Rows updated: {}", account, entry.first(), rowCount);
          }
        }
      }
    }
  }
}