/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.Account;
import io.finn.signald.BuildConfig;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import io.finn.signald.db.IAccountsTable;
import io.finn.signald.db.IServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.AddressUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public class AccountsTable implements IAccountsTable {
  private static final String TABLE_NAME = "signald_accounts";

  @Override
  public void add(String e164, ACI aci, java.util.UUID server) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s, %s) DO NOTHING", TABLE_NAME,
                              // FIELDS
                              UUID, E164, SERVER,
                              // ON CONFLICT
                              UUID, E164, SERVER);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setString(2, e164);
      statement.setObject(3, server);
      Database.executeUpdate(TABLE_NAME + "_add", statement);
      AddressUtil.addKnownAddress(new SignalServiceAddress(aci, e164));
    }
  }

  @Override
  public void DeleteAccount(ACI aci, String legacyUsername) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  @Override
  public void setUUID(JsonAddress address) throws SQLException {
    if (address.uuid == null || address.number == null) {
      throw new IllegalArgumentException("UUID or number is null");
    }
    var query = String.format("UPDATE %s SET %s=? WHERE %s=?", TABLE_NAME, UUID, E164);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, address.getACI().uuid());
      statement.setString(2, address.number);
      Database.executeUpdate(TABLE_NAME + "_set_uuid", statement);
    }
  }

  public ACI getACI(String e164) throws NoSuchAccountException, SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=?", UUID, TABLE_NAME, E164);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, e164);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_aci", statement)) {
        if (!rows.next()) {
          throw new NoSuchAccountException(e164);
        }
        return ACI.from(java.util.UUID.fromString(rows.getString(UUID)));
      }
    }
  }

  @Override
  public String getE164(ACI aci) throws NoSuchAccountException, SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=?", E164, TABLE_NAME, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_e164", statement)) {
        if (!rows.next()) {
          throw new NoSuchAccountException(aci.toString());
        }
        return rows.getString(E164);
      }
    }
  }

  @Override
  public IServersTable.AbstractServer getServer(ACI aci) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    var query = String.format("SELECT %s FROM %s WHERE %s=?", SERVER, TABLE_NAME, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_server", statement)) {
        if (!rows.next()) {
          throw new AssertionError("account not found");
        }
        String serverUUID = rows.getString(SERVER);
        if (serverUUID == null) {
          serverUUID = BuildConfig.DEFAULT_SERVER_UUID;
          setServer(aci, serverUUID);
        }
        return Database.Get().ServersTable.getServer(java.util.UUID.fromString(serverUUID));
      }
    }
  }

  @Override
  public void setServer(ACI aci, String server) throws SQLException {
    var query = String.format("UPDATE %s SET %s=? WHERE %s=?", TABLE_NAME, SERVER, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, server);
      statement.setObject(2, aci.uuid());
      Database.executeUpdate(TABLE_NAME + "_set_server", statement);
    }
  }

  @Override
  public DynamicCredentialsProvider getCredentialsProvider(ACI aci) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=?", E164, TABLE_NAME, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      Account account = new Account(aci);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_credentials_provider", statement)) {
        if (!rows.next()) {
          throw new AssertionError("account not found");
        }
        String e164 = rows.getString(E164);
        return new DynamicCredentialsProvider(account.getACI(), account.getPNI(), e164, account.getPassword(), account.getDeviceId());
      }
    }
  }

  @Override
  public boolean exists(ACI aci) throws SQLException {
    var query = String.format("SELECT 1 FROM %s WHERE %s=?", TABLE_NAME, UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      try (var rows = Database.executeQuery(TABLE_NAME + "_check_exists", statement)) {
        if (!rows.next()) {
          return false;
        }
      }
    }

    Boolean result = Database.Get().AccountDataTable.getBoolean(aci, IAccountDataTable.Key.PENDING_DELETION);
    if (result == null) {
      return true;
    }
    return !result;
  }

  @Override
  public List<ACI> getAll() throws SQLException {
    var query = String.format("SELECT %s FROM %s", UUID, TABLE_NAME);
    try (var statement = Database.getConn().prepareStatement(query)) {
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_all", statement)) {
        List<ACI> results = new ArrayList<>();
        while (rows.next()) {
          results.add(ACI.parseOrThrow(rows.getString(UUID)));
        }
        return results;
      }
    }
  }
}
