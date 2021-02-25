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

import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.storage.AddressResolver;
import io.finn.signald.util.AddressUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class RecipientsTable implements AddressResolver {
  private static final Logger logger = LogManager.getLogger();

  static final String TABLE_NAME = "recipients";
  static final String ROW_ID = "rowid";
  static final String ACCOUNT_UUID = "account_uuid";
  static final String UUID = "uuid";
  static final String E164 = "e164";

  private final UUID uuid;

  public RecipientsTable(java.util.UUID u) { uuid = u; }

  @Override
  public SignalServiceAddress resolve(SignalServiceAddress partial) {
    try {
      Pair<Integer, SignalServiceAddress> result = get(partial);
      return result.second();
    } catch (SQLException e) {
      logger.catching(e);
      return partial;
    }
  }

  @Override
  public SignalServiceAddress resolve(String identifier) {
    SignalServiceAddress address = AddressUtil.fromIdentifier(identifier);
    return resolve(address);
  }

  @Override
  public Collection<SignalServiceAddress> resolve(Collection<SignalServiceAddress> partials) {
    Collection<SignalServiceAddress> full = new ArrayList<>();
    for (SignalServiceAddress p : partials) {
      full.add(resolve(p));
    }
    return full;
  }

  public JsonAddress resolve(JsonAddress partial) {
    SignalServiceAddress full = partial.getSignalServiceAddress();
    return new JsonAddress(resolve(full));
  }

  public Pair<Integer, SignalServiceAddress> get(String address) throws SQLException { return get(AddressUtil.fromIdentifier(address)); }

  public Pair<Integer, SignalServiceAddress> get(SignalServiceAddress address) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + ROW_ID + "," + E164 + "," + UUID + " FROM " + TABLE_NAME + " WHERE (" + UUID + " = ? OR " + E164 +
                                                                      " = ?) AND " + ACCOUNT_UUID + " = ?");
    if (address.getUuid().isPresent()) {
      statement.setString(1, address.getUuid().get().toString());
    }

    if (address.getNumber().isPresent()) {
      statement.setString(2, address.getNumber().get());
    }

    statement.setString(3, uuid.toString());
    ResultSet rows = statement.executeQuery();
    List<Pair<Integer, SignalServiceAddress>> results = new ArrayList<>();
    while (rows.next()) {
      int rowid = rows.getInt(ROW_ID);
      String storedE164 = rows.getString(E164);
      String storedUUID = rows.getString(UUID);
      SignalServiceAddress a = new SignalServiceAddress(storedUUID == null ? null : java.util.UUID.fromString(storedUUID), storedE164);
      results.add(new Pair<>(rowid, a));
    }
    rows.close();

    if (results.size() == 0) {
      return new Pair<>(storeNew(address), address);
    }

    Pair<Integer, SignalServiceAddress> result = results.get(0);

    if (results.size() > 1) {
      logger.warn("recipient query returned multiple results, merging");
      int rowid = -1; //
      for (Pair<Integer, SignalServiceAddress> r : results) {
        if (rowid < 0 && r.second().getUuid().isPresent()) { // have not selected a preferred winner yet and this candidate has a UUID
          rowid = r.first();
          result = r;
        } else {
          logger.debug("Dropping row " + rowid + " with address " + new JsonAddress(r.second()).toRedactedString());
          delete(r.first());
        }
      }
    }

    int rowid = result.first();
    String storedUUID = result.second().getUuid().isPresent() ? result.second().getUuid().get().toString() : null;
    String storedE164 = result.second().getNumber().orNull();

    if (address.getUuid().isPresent() && storedUUID == null) {
      update(UUID, address.getUuid().get().toString(), result.first());
    }

    if (!address.getUuid().isPresent() && storedUUID != null) {
      address = new SignalServiceAddress(result.second().getUuid().get(), storedE164);
    }

    if (address.getNumber().isPresent() && storedE164 == null) {
      update(E164, address.getNumber().get(), rowid);
    }

    if (!address.getNumber().isPresent() && storedE164 != null) {
      UUID uuid = address.getUuid().isPresent() ? address.getUuid().get() : null;
      address = new SignalServiceAddress(uuid, storedE164);
    }

    return new Pair<>(rowid, address);
  }

  private int storeNew(SignalServiceAddress address) throws SQLException {
    Connection connection = Database.getConn();
    PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + UUID + "," + E164 + ") VALUES (?, ?, ?)");
    statement.setString(1, uuid.toString());
    if (address.getUuid().isPresent()) {
      statement.setString(2, address.getUuid().get().toString());
    }
    if (address.getNumber().isPresent()) {
      statement.setString(3, address.getNumber().get());
    }
    statement.executeUpdate();
    ResultSet rows = connection.prepareStatement("SELECT last_insert_rowid()").executeQuery();
    assert !rows.next();
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
}
