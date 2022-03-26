/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.ACI;

public interface ISessionsTable extends SessionStore {
  String ROW_ID = "rowid";
  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String DEVICE_ID = "device_id";
  String RECORD = "record";

  void deleteAccount(ACI aci) throws SQLException;
  Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> list);
  void archiveAllSessions(Recipient recipient) throws SQLException;

  default void deleteAllSessions(Recipient recipient) { deleteAllSessions(recipient.getAddress().getIdentifier()); }
}
