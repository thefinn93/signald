/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.db;

import io.finn.signald.SignalDependencies;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

public class RecipientsTable {
  private static final Logger logger = LogManager.getLogger();

  static final String TABLE_NAME = "recipients";
  static final String ROW_ID = "rowid";
  static final String ACCOUNT_UUID = "account_uuid";
  static final String UUID = "uuid";
  static final String E164 = "e164";

  private final UUID uuid;

  public RecipientsTable(java.util.UUID u) { uuid = u; }

  public List<Recipient> get(List<SignalServiceAddress> addresses) throws SQLException, IOException {
    List<Recipient> results = new ArrayList<>();
    for (SignalServiceAddress address : addresses) {
      results.add(get(address));
    }
    return results;
  }

  public Recipient get(SignalServiceAddress address) throws SQLException, IOException { return get(address.getNumber().orNull(), address.getUuid()); }

  public Recipient get(JsonAddress address) throws IOException, SQLException { return get(address.number, address.getUUID()); }

  public Recipient get(UUID query) throws IOException, SQLException { return get(null, query); }

  public Recipient get(String identifier) throws IOException, SQLException {
    if (identifier.startsWith("+")) {
      return get(identifier, null);
    } else {
      return get(null, java.util.UUID.fromString(identifier));
    }
  }

  public Recipient get(String e164, UUID queryUUID) throws SQLException, IOException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + ROW_ID + "," + E164 + "," + UUID + " FROM " + TABLE_NAME + " WHERE (" + UUID + " = ? OR " + E164 +
                                                                      " = ?) AND " + ACCOUNT_UUID + " = ?");
    if (queryUUID != null) {
      statement.setString(1, queryUUID.toString());
    }

    if (e164 != null) {
      statement.setString(2, e164);
    }

    statement.setString(3, uuid.toString());
    ResultSet rows = statement.executeQuery();
    List<Recipient> results = new ArrayList<>();
    while (rows.next()) {
      int rowid = rows.getInt(ROW_ID);
      String storedE164 = rows.getString(E164);
      String storedUUID = rows.getString(UUID);
      SignalServiceAddress a = storedUUID == null ? null : new SignalServiceAddress(java.util.UUID.fromString(storedUUID), storedE164);
      results.add(new Recipient(uuid, rowid, a));
    }
    rows.close();

    int rowid = -1;
    UUID storedUUID = null;
    String storedE164 = null;
    if (results.size() > 0) {
      Recipient result = results.get(0);
      rowid = result.getId();

      if (results.size() > 1) {
        logger.warn("recipient query returned multiple results, merging");
        for (Recipient r : results) {
          if (rowid < 0 && r.getAddress() != null) { // have not selected a preferred winner yet and this candidate has a UUID
            rowid = r.getId();
            result = r;
          } else {
            logger.debug("Dropping duplicate recipient row id = " + rowid);
            delete(r.getId());
          }
        }
      }

      storedUUID = result.getAddress() != null ? result.getAddress().getUuid() : null;
      storedE164 = result.getAddress() != null ? result.getAddress().getNumber().orNull() : null;
      rowid = result.getId();
    }

    // query included a UUID that wasn't in the database
    if (queryUUID != null && storedUUID == null) {
      if (rowid < 0) {
        rowid = storeNew(queryUUID, e164);
        storedE164 = e164;
      } else {
        update(UUID, queryUUID.toString(), rowid);
      }
      storedUUID = queryUUID;
    }

    // query included an e164 that wasn't in the database
    if (e164 != null && storedE164 == null) {
      update(E164, e164, rowid);
      storedE164 = e164;
    }

    if (storedUUID == null) {
      storedUUID = getRegisteredUser(e164);
      if (rowid > 0) {
        update(UUID, storedUUID.toString(), rowid);
      } else {
        rowid = storeNew(storedUUID, e164);
      }
    }

    if (rowid == -1 && queryUUID != null) {
      rowid = storeNew(queryUUID, e164);
    }

    return new Recipient(uuid, rowid, new SignalServiceAddress(storedUUID, storedE164));
  }

  private int storeNew(UUID newUUID, String e164) throws SQLException {
    Connection connection = Database.getConn();
    PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + UUID + "," + E164 + ") VALUES (?, ?, ?)");
    statement.setString(1, uuid.toString());
    statement.setString(2, newUUID.toString());
    if (e164 != null) {
      statement.setString(3, e164);
    }
    statement.executeUpdate();
    ResultSet rows = connection.prepareStatement("SELECT last_insert_rowid()").executeQuery();
    if (!rows.next()) {
      throw new AssertionError("error fetching ID of last row inserted while storing " + newUUID.toString() + "/" + e164);
    }
    int rowid = rows.getInt(1);
    rows.close();
    return rowid;
  }

  private void update(String column, String value, int row) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("UPDATE " + TABLE_NAME + " SET " + column + " = ? WHERE " + ACCOUNT_UUID + " = ? AND " + ROW_ID + " = ?");
    statement.setString(1, value);
    statement.setString(2, uuid.toString());
    statement.setInt(3, row);
    statement.executeUpdate();
  }

  private void delete(int row)throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ROW_ID + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setInt(1, row);
    statement.setString(2, uuid.toString());
    statement.executeUpdate();
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }

  private UUID getRegisteredUser(final String number) throws IOException, SQLException {
    final Map<String, UUID> uuidMap;
    try {
      Set<String> numbers = new HashSet<>();
      numbers.add(number);
      uuidMap = getRegisteredUsers(numbers);
    } catch (NumberFormatException e) {
      throw new UnregisteredUserException(number, e);
    } catch (InvalidProxyException | ServerNotFoundException | NoSuchAccountException e) {
      logger.error("error resolving UUIDs: ", e);
      throw new IOException(e);
    }
    java.util.UUID discoveredUUID = uuidMap.get(number);
    if (discoveredUUID == null) {
      throw new UnregisteredUserException(number, null);
    }
    return discoveredUUID;
  }

  private Map<String, UUID> getRegisteredUsers(final Set<String> numbers) throws IOException, InvalidProxyException, SQLException, ServerNotFoundException, NoSuchAccountException {
    final Map<String, UUID> registeredUsers;
    ServersTable.Server server = AccountsTable.getServer(uuid);
    SignalServiceAccountManager accountManager = SignalDependencies.get(uuid).getAccountManager();
    logger.debug("querying server for UUIDs of " + numbers.size() + " e164 identifiers");
    try {
      registeredUsers = accountManager.getRegisteredUsers(server.getIASKeyStore(), numbers, server.getCdsMrenclave());
    } catch (InvalidKeyException | KeyStoreException | CertificateException | NoSuchAlgorithmException | Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException |
             SignatureException | UnauthenticatedResponseException e) {
      throw new IOException(e);
    }

    return registeredUsers;
  }
}
