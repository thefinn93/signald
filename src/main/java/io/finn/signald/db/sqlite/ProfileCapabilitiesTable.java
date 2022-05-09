package io.finn.signald.db.sqlite;

import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.IProfileCapabilitiesTable;
import io.finn.signald.db.Recipient;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.push.ACI;

public class ProfileCapabilitiesTable implements IProfileCapabilitiesTable {
  private static final String TABLE_NAME = "profile_capabilities";

  private final Account account;

  public ProfileCapabilitiesTable(ACI aci) { account = new Account(aci); }

  @Override
  public void set(Recipient recipient, Capabilities capabilities) throws SQLException {
    String query =
        String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (%s, %S) DO UPDATE SET %s=excluded.%s, %s=excluded.%s, "
                          + "%s=excluded.%s, %s=excluded.%s, %s=excluded.%s, %s=excluded.%s;",
                      TABLE_NAME, ACCOUNT_UUID, RECIPIENT, STORAGE, GV1_MIGRATION, SENDER_KEY, ANNOUNCEMENT_GROUP, CHANGE_NUMBER, STORIES, ACCOUNT_UUID, RECIPIENT, STORAGE,
                      STORAGE, GV1_MIGRATION, GV1_MIGRATION, SENDER_KEY, SENDER_KEY, ANNOUNCEMENT_GROUP, ANNOUNCEMENT_GROUP, CHANGE_NUMBER, CHANGE_NUMBER, STORIES, STORIES);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account.getACI().toString());
      statement.setInt(2, recipient.getId());
      statement.setBoolean(3, capabilities.isStorage());
      statement.setBoolean(4, capabilities.isGv1Migration());
      statement.setBoolean(5, capabilities.isSenderKey());
      statement.setBoolean(6, capabilities.isAnnouncementGroup());
      statement.setBoolean(7, capabilities.isChangeNumber());
      statement.setBoolean(8, capabilities.isStories());
      Database.executeUpdate(TABLE_NAME + "_set", statement);
    }
  }

  @Override
  public boolean get(Recipient recipient, String capability) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? LIMIT 1", capability, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account.getACI().toString());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_" + capability, statement)) {
        if (!rows.next()) {
          return false;
        }
        return rows.getBoolean(capability);
      }
    }
  }

  public Capabilities getAll(Recipient recipient) throws SQLException {
    var query = String.format("SELECT * FROM %s WHERE %s=? AND %s=? LIMIT 1", TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account.getACI().toString());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_all", statement)) {
        if (!rows.next()) {
          return null;
        }
        Capabilities capabilities = new Capabilities();
        capabilities.setStorage(rows.getBoolean(STORAGE));
        capabilities.setGv1Migration(rows.getBoolean(GV1_MIGRATION));
        capabilities.setSenderKey(rows.getBoolean(SENDER_KEY));
        capabilities.setAnnouncementGroup(rows.getBoolean(ANNOUNCEMENT_GROUP));
        capabilities.setChangeNumber(rows.getBoolean(CHANGE_NUMBER));
        capabilities.setStorage(rows.getBoolean(STORIES));
        return capabilities;
      }
    }
  }
}
