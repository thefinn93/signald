package io.finn.signald.db.postgresql;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.IProfileBadgesTable;
import io.finn.signald.util.JSONUtil;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.push.ACI;

public class ProfileBadgesTable implements IProfileBadgesTable {
  private static final String TABLE_NAME = "signald_profile_badges";
  private final Account account;

  public ProfileBadgesTable(ACI aci) { account = new Account(aci); }

  @Override
  public Badge get(String id) throws SQLException, JsonProcessingException {
    var query = String.format("SELECT %s, %s, %s, %s, %s FROM %s WHERE %s=? AND %s=? LIMIT 1", ID, CATEGORY, NAME, DESCRIPTION, SPRITE6, TABLE_NAME, ACCOUNT_UUID, ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setString(2, id);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_badge", statement)) {
        if (!rows.next()) {
          return null;
        }

        return new Badge(rows);
      }
    }
  }

  @Override
  public void set(Badge badge) throws SQLException, JsonProcessingException {
    var query = String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (%s, %s) "
                                  + "DO UPDATE SET %s = excluded.%s, %s = excluded.%s, %s = excluded.%s, %s = excluded.%s",
                              TABLE_NAME, ACCOUNT_UUID, ID, CATEGORY, NAME, DESCRIPTION, SPRITE6, ACCOUNT_UUID, ID, CATEGORY, CATEGORY, NAME, NAME, DESCRIPTION, DESCRIPTION,
                              SPRITE6, SPRITE6);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setString(2, badge.getId());
      statement.setString(3, badge.getCategory());
      statement.setString(4, badge.getName());
      statement.setString(5, badge.getDescription());
      statement.setString(6, JSONUtil.GetWriter().writeValueAsString(badge.getSprites6()));
      Database.executeUpdate(TABLE_NAME + "_set_badge", statement);
    }
  }
}
