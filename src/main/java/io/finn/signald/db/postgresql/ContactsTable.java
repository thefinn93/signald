package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IContactsTable;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ContactsTable implements IContactsTable {
  private static final String TABLE_NAME = "signald_contacts";

  private final String RECIPIENT_ACI = "uuid";
  private final String RECIPIENT_E164 = "e164";

  private final ACI aci;

  public ContactsTable(ACI aci) { this.aci = aci; }

  public ACI getACI() { return aci; }

  private ContactInfo infoFromRow(ResultSet row) throws SQLException {
    var serviceAddress = new SignalServiceAddress(ACI.from(UUID.fromString(row.getString(RECIPIENT_ACI))), row.getString(RECIPIENT_E164));
    return new ContactInfo(row.getString(NAME), new Recipient(aci.uuid(), row.getInt(RECIPIENT), serviceAddress), row.getString(COLOR), row.getBytes(PROFILE_KEY),
                           row.getInt(MESSAGE_EXPIRATION_TIME), row.getInt(INBOX_POSITION));
  }

  @Override
  public ContactInfo get(Recipient recipient) throws SQLException {
    var query = String.format("SELECT %s, %s.%s, %s.%s, %s, %s, %s, %s, %s FROM %s JOIN %s ON %s.%s = %s.%s WHERE %s.%s=? AND %s=?",
                              // FIELDS
                              RECIPIENT, RecipientsTable.TABLE_NAME, RECIPIENT_ACI, RecipientsTable.TABLE_NAME, RECIPIENT_E164, NAME, COLOR, PROFILE_KEY, MESSAGE_EXPIRATION_TIME,
                              INBOX_POSITION,
                              // FROM
                              TABLE_NAME,
                              // JOIN
                              RecipientsTable.TABLE_NAME, TABLE_NAME, RECIPIENT, RecipientsTable.TABLE_NAME, IRecipientsTable.ROW_ID,
                              // WHERE
                              TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get", statement)) {
        return rows.next() ? infoFromRow(rows) : null;
      }
    }
  }

  @Override
  public ArrayList<ContactInfo> getAll() throws SQLException {
    var query = String.format("SELECT %s, %s.%s, %s.%s, %s, %s, %s, %s, %s FROM %s JOIN %s ON %s.%s = %s.%s WHERE %s.%s=?",
                              // FIELDS
                              RECIPIENT, RecipientsTable.TABLE_NAME, RECIPIENT_ACI, RecipientsTable.TABLE_NAME, RECIPIENT_E164, NAME, COLOR, PROFILE_KEY, MESSAGE_EXPIRATION_TIME,
                              INBOX_POSITION,
                              // FROM
                              TABLE_NAME,
                              // JOIN
                              RecipientsTable.TABLE_NAME, TABLE_NAME, RECIPIENT, RecipientsTable.TABLE_NAME, IRecipientsTable.ROW_ID,
                              // WHERE
                              TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get", statement)) {
        var contactInfos = new ArrayList<ContactInfo>();
        while (rows.next()) {
          contactInfos.add(infoFromRow(rows));
        }
        return contactInfos;
      }
    }
  }

  @Override
  public ContactInfo update(Recipient recipient, String name, String color, byte[] profileKey, Integer messageExpirationTime, Integer inboxPosition) throws SQLException {
    var updates = new ArrayList<Pair<String, Object>>();
    if (messageExpirationTime != null) {
      updates.add(new Pair<>(MESSAGE_EXPIRATION_TIME, messageExpirationTime));
    }
    if (name != null) {
      updates.add(new Pair<>(NAME, name));
    }
    if (color != null) {
      updates.add(new Pair<>(COLOR, color));
    }
    if (profileKey != null) {
      updates.add(new Pair<>(PROFILE_KEY, profileKey));
    }
    if (inboxPosition != null) {
      updates.add(new Pair<>(INBOX_POSITION, inboxPosition));
    }

    var fieldStr = updates.stream().map(Pair::first).collect(Collectors.joining(","));
    var valueStr = String.join(",", Collections.nCopies(updates.size(), "?"));
    var setStr = updates.stream().map(p -> String.format("%s=EXCLUDED.%s", p.first(), p.first())).collect(Collectors.joining(","));
    var query = String.format("WITH inserted_contact AS ("
                                  + "       INSERT INTO %s (%s, %s, %s) VALUES (?, ?, %s)"
                                  + "       ON CONFLICT (%s, %s) DO UPDATE SET %s"
                                  + "       RETURNING %s, %s, %s, %s, %s, %s"
                                  + "     )"
                                  + "     SELECT %s, %s.%s, %s.%s, %s, %s, %s, %s, %s"
                                  + "       FROM inserted_contact"
                                  + "       JOIN %s ON inserted_contact.%s = %s.%s",
                              // INSERT INTO
                              TABLE_NAME, ACCOUNT_UUID, RECIPIENT, fieldStr, valueStr,
                              // ON CONFLICT
                              ACCOUNT_UUID, RECIPIENT,
                              // DO UPDATE SET
                              setStr,
                              // RETURNING
                              RECIPIENT, NAME, COLOR, PROFILE_KEY, MESSAGE_EXPIRATION_TIME, INBOX_POSITION,
                              // SELECT
                              RECIPIENT, RecipientsTable.TABLE_NAME, RECIPIENT_ACI, RecipientsTable.TABLE_NAME, RECIPIENT_E164, NAME, COLOR, PROFILE_KEY, MESSAGE_EXPIRATION_TIME,
                              INBOX_POSITION,
                              // JOIN
                              RecipientsTable.TABLE_NAME, RECIPIENT, RecipientsTable.TABLE_NAME, IRecipientsTable.ROW_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      // Account UUID
      statement.setObject(1, aci.uuid());
      // Recipient ID
      statement.setInt(2, recipient.getId());
      int i = 3;
      for (var update : updates) {
        statement.setObject(i++, update.second());
      }
      try (var rows = Database.executeQuery(TABLE_NAME + "_update", statement)) {
        return rows.next() ? infoFromRow(rows) : null;
      }
    }
  }

  @Override
  public void clear() throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      var deletedCount = Database.executeUpdate(TABLE_NAME + "_clear", statement);
      logger.info("Deleted " + deletedCount + "contacts for " + aci);
    }
  }

  @Override
  public void addBatch(List<ContactInfo> contacts) throws SQLException {
    for (var contact : contacts) {
      update(contact);
    }
  }
}
