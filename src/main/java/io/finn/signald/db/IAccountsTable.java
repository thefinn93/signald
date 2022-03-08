/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.Account;
import io.finn.signald.BuildConfig;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.AccountData;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public interface IAccountsTable {
  Logger logger = LogManager.getLogger();

  String UUID = "uuid";
  String E164 = "e164";
  String FILENAME = "filename";
  String SERVER = "server";

  File getFile(ACI aci) throws SQLException, NoSuchAccountException;
  File getFile(String e164) throws SQLException, NoSuchAccountException;
  void add(String e164, ACI aci, String filename, UUID server) throws SQLException;
  void DeleteAccount(ACI aci, UUID uuid, String legacyUsername) throws SQLException;
  void setUUID(JsonAddress address) throws SQLException;
  ACI getACI(String e164) throws SQLException, NoSuchAccountException;
  String getE164(ACI aci) throws SQLException, NoSuchAccountException;
  IServersTable.AbstractServer getServer(ACI aci) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException;
  void setServer(ACI aci, String server) throws SQLException;
  DynamicCredentialsProvider getCredentialsProvider(ACI aci) throws SQLException;
  boolean exists(ACI aci) throws SQLException;
  List<UUID> getAll() throws SQLException;

  // Default implementations
  default boolean exists(UUID uuid) throws SQLException { return exists(ACI.from(uuid)); }
  default UUID getUUID(String e164) throws SQLException, NoSuchAccountException { return getACI(e164).uuid(); }
  default IServersTable.AbstractServer getServer(java.util.UUID uuid) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    return getServer(ACI.from(uuid));
  }

  default void importFromJSON(File f) throws IOException, SQLException, InvalidInputException {
    AccountData accountData = AccountData.load(f);
    if (accountData.getUUID() == null) {
      logger.warn("unable to import account with no UUID: " + accountData.getLegacyUsername());
      return;
    }
    logger.info("migrating account if needed: " + accountData.address.toRedactedString());
    add(accountData.getLegacyUsername(), accountData.address.getACI(), f.getAbsolutePath(), java.util.UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID));
    boolean needsSave = false;
    Account account = new Account(accountData.getUUID());
    try {
      if (accountData.legacyProtocolStore != null) {
        accountData.legacyProtocolStore.migrateToDB(account);
        accountData.legacyProtocolStore = null;
        needsSave = true;
      }
      if (accountData.legacyRecipientStore != null) {
        accountData.legacyRecipientStore.migrateToDB(account);
        accountData.legacyRecipientStore = null;
        needsSave = true;
      }

      if (accountData.legacyBackgroundActionsLastRun != null) {
        accountData.legacyBackgroundActionsLastRun.migrateToDB(account);
        accountData.legacyBackgroundActionsLastRun = null;
        needsSave = true;
      }

      if (accountData.legacyGroupsV2 != null) {
        needsSave = accountData.legacyGroupsV2.migrateToDB(account) || needsSave;
      }

      needsSave = accountData.migrateToDB(account) || needsSave;
    } finally {
      if (needsSave) {
        accountData.save();
      }
    }
  }
}
