/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.io.IOException;
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
import org.whispersystems.signalservice.api.push.ACI;

public class GroupCredentialsTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "group_credentials";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String DATE = "date";
  private static final String CREDENTIAL = "credential";

  private final ACI aci;

  public GroupCredentialsTable(ACI aci) { this.aci = aci; }

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
    var query = "SELECT " + CREDENTIAL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DATE + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setInt(2, date);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_credential", statement)) {
        return rows.next() ? Optional.of(new AuthCredentialResponse(rows.getBytes(CREDENTIAL))) : Optional.absent();
      }
    }
  }

  public void setCredentials(HashMap<Integer, AuthCredentialResponse> credentials) throws SQLException {
    var query = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" + ACCOUNT_UUID + "," + DATE + "," + CREDENTIAL + ") VALUES (?, ?, ?)";
    try (var statement = Database.getConn().prepareStatement(query)) {
      for (Map.Entry<Integer, AuthCredentialResponse> entry : credentials.entrySet()) {
        statement.setString(1, aci.toString());
        statement.setInt(2, entry.getKey());
        statement.setBytes(3, entry.getValue().serialize());
        Database.executeUpdate(TABLE_NAME + "_set_credentials", statement);
      }
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, uuid.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
