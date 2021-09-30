/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.db;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;

public class GroupCredentialsTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "group_credentials";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String DATE = "date";
  private static final String CREDENTIAL = "credential";

  private final UUID accountUUID;

  public GroupCredentialsTable(UUID accountUUID) { this.accountUUID = accountUUID; }

  public AuthCredentialResponse getCredential(GroupsV2Api groupsV2Api, int today) throws InvalidInputException, SQLException, IOException {
    Optional<AuthCredentialResponse> todaysCredentials = getCredential(today);
    if (!todaysCredentials.isPresent()) {
      logger.debug("refreshing group credentials");
      setCredentials(groupsV2Api.getCredentials(today));
      todaysCredentials = getCredential(today);
    }
    return todaysCredentials.get();
  }

  private Optional<AuthCredentialResponse> getCredential(int date) throws SQLException, InvalidInputException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + CREDENTIAL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DATE + " = ?");
    statement.setString(1, accountUUID.toString());
    statement.setInt(2, date);
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return Optional.absent();
    }
    return Optional.of(new AuthCredentialResponse(rows.getBytes(CREDENTIAL)));
  }

  public void setCredentials(HashMap<Integer, AuthCredentialResponse> credentials) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + " (" + ACCOUNT_UUID + "," + DATE + "," + CREDENTIAL + ") VALUES (?, ?, ?)");
    for (Map.Entry<Integer, AuthCredentialResponse> entry : credentials.entrySet()) {
      statement.setString(1, accountUUID.toString());
      statement.setInt(2, entry.getKey());
      statement.setBytes(3, entry.getValue().serialize());
      statement.executeUpdate();
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
