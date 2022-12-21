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
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.UnregisteredUserError;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.LegacyAccountData;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public interface IAccountsTable {
  Logger logger = LogManager.getLogger();

  String UUID = "uuid";
  String E164 = "e164";
  String SERVER = "server";

  void add(String e164, ACI aci, UUID server) throws SQLException;
  void DeleteAccount(ACI aci, String legacyUsername) throws SQLException;
  void setUUID(JsonAddress address) throws SQLException;
  ACI getACI(String e164) throws NoSuchAccountException, SQLException;
  String getE164(ACI aci) throws NoSuchAccountException, SQLException;
  IServersTable.AbstractServer getServer(ACI aci) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException;
  void setServer(ACI aci, String server) throws SQLException;
  DynamicCredentialsProvider getCredentialsProvider(ACI aci) throws SQLException;
  boolean exists(ACI aci) throws SQLException;
  List<ACI> getAll() throws SQLException;

  // Default implementations
  default boolean exists(UUID uuid) throws SQLException { return exists(ACI.from(uuid)); }
  default UUID getUUID(String e164) throws NoSuchAccountException, SQLException { return getACI(e164).uuid(); }
  default IServersTable.AbstractServer getServer(java.util.UUID uuid) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    return getServer(ACI.from(uuid));
  }

  @SuppressWarnings({"deprecation"})
  default void importFromLegacyJSON(File f) throws IOException, SQLException, InvalidInputException, UnregisteredUserError, InternalError {
    LegacyAccountData accountData = LegacyAccountData.load(f);
    if (accountData.address.uuid == null) {
      logger.warn("unable to import account with no UUID: " + accountData.getLegacyUsername());
      return;
    }
    logger.info("migrating account if needed: " + accountData.address.toRedactedString());
    add(accountData.getLegacyUsername(), accountData.address.getACI(), java.util.UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID));
    Account account = new Account(accountData.getACI());
    try {
      if (accountData.legacyProtocolStore != null) {
        accountData.legacyProtocolStore.migrateToDB(account);
        accountData.legacyProtocolStore = null;
      }
      if (accountData.legacyRecipientStore != null) {
        accountData.legacyRecipientStore.migrateToDB(account);
        accountData.legacyRecipientStore = null;
      }

      if (accountData.legacyBackgroundActionsLastRun != null) {
        accountData.legacyBackgroundActionsLastRun.migrateToDB(account);
        accountData.legacyBackgroundActionsLastRun = null;
      }

      if (accountData.legacyGroupsV2 != null) {
        accountData.legacyGroupsV2.migrateToDB(account);
      }

      if (accountData.legacyContactStore != null) {
        accountData.legacyContactStore.migrateToDB(account);
        accountData.legacyContactStore = null;
      }

      if (accountData.legacyProfileCredentialStore != null) {
        accountData.legacyProfileCredentialStore.migrateToDB(account);
        accountData.legacyProfileCredentialStore = null;
      }

      accountData.migrateToDB(account);

      accountData.version = LegacyAccountData.DELETED_DO_NOT_SAVE;
      logger.info("account fully migrated out of legacy storage, deleting legacy storage file");
      accountData.delete();
    } finally {
      accountData.save();
    }
  }
}
