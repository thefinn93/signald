package io.finn.signald.db.postgresql;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.IProfilesTable;
import io.finn.signald.db.Recipient;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class ProfilesTable implements IProfilesTable {
  private static final String TABLE_NAME = "signald_profiles";
  private final Account account;

  public ProfilesTable(ACI aci) { account = new Account(aci); }

  @Override
  public Profile get(Recipient recipient) throws SQLException {
    var query = String.format("SELECT %s, %s, %s, %s, %s, %s, %s FROM %s WHERE %s=? AND %s=? LIMIT 1", LAST_UPDATE, GIVEN_NAME, FAMILY_NAME, ABOUT, EMOJI, PAYMENT_ADDRESS, BADGES,
                              TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_profile", statement)) {
        if (!rows.next()) {
          return null;
        }
        return new Profile(rows);
      }
    }
  }

  private void set(Recipient recipient, String field, String value) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s = excluded.%s, %s = excluded.%s", TABLE_NAME, ACCOUNT_UUID,
                              RECIPIENT, field, LAST_UPDATE, ACCOUNT_UUID, RECIPIENT, field, field, LAST_UPDATE, LAST_UPDATE);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      statement.setString(3, value == null ? "" : value);
      statement.setLong(4, System.currentTimeMillis());
      Database.executeUpdate(TABLE_NAME + "_set_" + field, statement);
    }
  }

  @Override
  public void setPaymentAddress(Recipient recipient, SignalServiceProtos.PaymentAddress paymentAddress) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s = excluded.%s, %s = excluded.%s", TABLE_NAME, ACCOUNT_UUID,
                              RECIPIENT, PAYMENT_ADDRESS, LAST_UPDATE, ACCOUNT_UUID, RECIPIENT, PAYMENT_ADDRESS, PAYMENT_ADDRESS, LAST_UPDATE, LAST_UPDATE);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      statement.setBytes(3, paymentAddress == null ? null : paymentAddress.toByteArray());
      statement.setLong(4, System.currentTimeMillis());
      Database.executeUpdate(TABLE_NAME + "_set_" + PAYMENT_ADDRESS, statement);
    }
  }

  @Override
  public void setSerializedName(Recipient recipient, String name) throws SQLException {
    if (name == null) {
      set(recipient, GIVEN_NAME, "");
      set(recipient, FAMILY_NAME, "");
      return;
    }
    String[] parts = name.split("\0");
    if (parts.length == 0) {
      set(recipient, GIVEN_NAME, "");
      set(recipient, FAMILY_NAME, "");
    } else if (parts.length == 1) {
      set(recipient, GIVEN_NAME, parts[0]);
      set(recipient, FAMILY_NAME, "");
    } else {
      set(recipient, GIVEN_NAME, parts[0]);
      set(recipient, FAMILY_NAME, parts[1]);
    }
  }

  @Override
  public void setAbout(Recipient recipient, String about) throws SQLException {
    set(recipient, ABOUT, about);
  }

  @Override
  public void setEmoji(Recipient recipient, String emoji) throws SQLException {
    set(recipient, EMOJI, emoji);
  }

  @Override
  public void setBadges(Recipient recipient, List<SignalServiceProfile.Badge> badges) throws SQLException, JsonProcessingException {
    set(recipient, BADGES, StoredBadge.serialize(badges));
  }
}
