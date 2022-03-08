/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.SignalDependencies;
import io.finn.signald.db.Database;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.sentry.Sentry;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

public class RecipientsTable implements IRecipientsTable {
  private static final Logger logger = LogManager.getLogger();

  static final String TABLE_NAME = "signald_recipients";

  private final UUID accountUUID;

  public RecipientsTable(ACI aci) { accountUUID = aci.uuid(); }

  @Override
  public synchronized Recipient get(String queryE164, ACI queryACI) throws SQLException, IOException {
    logger.trace("looking up recipient {}/{}", queryE164, queryACI);
    List<Recipient> results = new ArrayList<>();
    var query = String.format("SELECT %s, %s, %s FROM %s WHERE (%s=? OR %s=?) AND %s=?",
                              // FIELDS
                              ROW_ID, E164, UUID,
                              // FROM
                              TABLE_NAME,
                              // WHERE
                              UUID, E164, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, queryACI != null ? queryACI.uuid() : null);
      statement.setString(2, queryE164);
      statement.setObject(3, accountUUID);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get", statement)) {
        while (rows.next()) {
          int rowid = rows.getInt(ROW_ID);
          String storedE164 = rows.getString(E164);
          UUID storedUUID = rows.getObject(UUID, java.util.UUID.class);
          SignalServiceAddress a = storedUUID == null ? null : new SignalServiceAddress(ACI.from(storedUUID), storedE164);
          results.add(new Recipient(accountUUID, rowid, a));
          logger.trace("found result with rowid {} ({}/{})", rowid, storedE164, storedUUID);
        }
      }
    }

    int rowid = -1;
    ACI storedACI = null;
    String storedE164 = null;
    if (results.size() > 0) {
      logger.trace("at least one result returned");
      Recipient result = results.get(0);
      rowid = result.getId();

      if (results.size() > 1) {
        logger.warn("recipient query returned multiple results, merging");
        for (Recipient r : results) {
          if (rowid < 0 && r.getAddress() != null) { // have not selected a preferred winner yet and this candidate has a UUID
            rowid = r.getId();
            result = r;
            logger.trace("candidate {} has a uuid, will be considered winner", rowid);
          } else {
            logger.debug("Dropping duplicate recipient row id = " + rowid);
            delete(r.getId());
          }
        }
      }

      storedACI = result.getAddress() != null ? result.getACI() : null;
      storedE164 = result.getAddress() != null ? result.getAddress().getNumber().orNull() : null;
      rowid = result.getId();
    }

    // query included a UUID that wasn't in the database
    if (queryACI != null && storedACI == null) {
      logger.trace("query included a UUID that wasn't in the database");
      if (rowid < 0) {
        logger.trace("no row in the database, storing new recipient");
        rowid = storeNew(queryACI, queryE164);
        storedE164 = queryE164;
      } else {
        logger.trace("updating existing row in database");
        update(UUID, queryACI.uuid(), rowid);
      }
      storedACI = queryACI;
    }

    // query included an e164 that wasn't in the database
    if (queryE164 != null && rowid > -1 && storedE164 == null) {
      logger.trace("query included an e164 that wasn't in the database, updating");
      update(E164, queryE164, rowid);
      storedE164 = queryE164;
    }

    // phone number change
    if (queryE164 != null && !queryE164.equals(storedE164)) {
      // TODO: notify clients?
      logger.trace("ACI {} changed numbers, updating row id {}", queryACI, rowid);
      update(E164, queryE164, rowid);
    }

    // query did not include a UUID
    if (storedACI == null) {
      logger.trace("query did not include a UUID, asking server");
      // ask the server for the UUID (throws UnregisteredUserException if the e164 isn't registered)
      storedACI = getRegisteredUser(queryE164);
      logger.trace("got result");

      if (rowid > 0) {
        // if the e164 was in the database already, update the existing row
        update(UUID, storedACI.uuid(), rowid);
      } else {
        // if the e164 was not in the database, re-run the get() with both e164 and UUID
        // can't just insert because the newly-discovered UUID might already be in the database
        logger.trace("query included an e164 that wasn't in the database, we resolved the ACI but we're going to recurse just in case that ACI is already known");
        return get(queryE164, storedACI);
      }
    }

    if (rowid == -1 && queryACI != null) {
      rowid = storeNew(queryACI, queryE164);
    }

    logger.trace("returning recipient {}", rowid);
    return new Recipient(accountUUID, rowid, new SignalServiceAddress(storedACI, storedE164));
  }

  private int storeNew(ACI aci, String e164) throws SQLException {
    logger.trace("storing new recipient {}/{}", e164, aci);
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) RETURNING %s", TABLE_NAME, ACCOUNT_UUID, UUID, E164, ROW_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, accountUUID);
      statement.setObject(2, aci.uuid());
      statement.setString(3, e164);
      try (var insertResult = Database.executeQuery(TABLE_NAME + "_store_name", statement)) {
        if (!insertResult.next()) {
          throw new AssertionError("error fetching ID of last row inserted while storing " + aci + "/" + e164);
        }
        int rowid = insertResult.getInt(ROW_ID);
        logger.trace("stored recipient {}", rowid);
        return rowid;
      }
    }
  }

  private void update(String column, Object value, int row) throws SQLException {
    logger.trace("updating recipient {} with {} = {}", row, column, value);
    var query = String.format("UPDATE %s SET %s=? WHERE %s=? AND %s=?", TABLE_NAME, column, ACCOUNT_UUID, ROW_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, value);
      statement.setObject(2, accountUUID);
      statement.setInt(3, row);
      Database.executeUpdate(TABLE_NAME + "_update", statement);
    }
    logger.trace("updated");
  }

  private void delete(int row)throws SQLException {
    logger.trace("deleting recipient {}", row);
    var query = String.format("DELETE FROM %s WHERE %s=? AND %s=?", TABLE_NAME, ROW_ID, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setInt(1, row);
      statement.setObject(2, accountUUID);
      Database.executeUpdate(TABLE_NAME + "_delete", statement);
    }
    logger.trace("deleted");
  }

  @Override
  public void deleteAccount(UUID uuid) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, uuid);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  private ACI getRegisteredUser(final String number) throws IOException, SQLException {
    final Map<String, ACI> aciMap;
    try {
      Set<String> numbers = new HashSet<>();
      numbers.add(number);
      aciMap = getRegisteredUsers(numbers);
    } catch (NumberFormatException e) {
      throw new UnregisteredUserException(number, e);
    } catch (InvalidProxyException | ServerNotFoundException | NoSuchAccountException e) {
      logger.error("error resolving UUIDs: ", e);
      Sentry.captureException(e);
      throw new IOException(e);
    }
    ACI aci = aciMap.get(number);
    if (aci == null) {
      throw new UnregisteredUserException(number, null);
    }
    return aci;
  }

  private Map<String, ACI> getRegisteredUsers(final Set<String> numbers) throws IOException, InvalidProxyException, SQLException, ServerNotFoundException, NoSuchAccountException {
    final Map<String, ACI> registeredUsers;
    var server = Database.Get().AccountsTable.getServer(accountUUID);
    SignalServiceAccountManager accountManager = SignalDependencies.get(accountUUID).getAccountManager();
    logger.debug("querying server for UUIDs of {} e164 identifiers", numbers.size());
    try {
      registeredUsers = accountManager.getRegisteredUsers(server.getIASKeyStore(), numbers, server.getCdsMrenclave());
    } catch (InvalidKeyException | KeyStoreException | CertificateException | NoSuchAlgorithmException | Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException |
             SignatureException | UnauthenticatedResponseException e) {
      throw new IOException(e);
    }
    logger.trace("got {} results from server", registeredUsers.size());

    return registeredUsers;
  }
}
