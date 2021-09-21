/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald;

import io.finn.signald.db.AccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

public class LinkedDeviceManager {
  private final UUID accountUUID;
  private final SignalDependencies dependencies;

  public LinkedDeviceManager(UUID accountUUID) throws SQLException, InvalidProxyException, IOException, ServerNotFoundException {
    this.accountUUID = accountUUID;
    this.dependencies = SignalDependencies.get(accountUUID);
  }

  public List<DeviceInfo> getLinkedDevices() throws IOException, SQLException {
    List<DeviceInfo> devices = dependencies.getAccountManager().getDevices();
    AccountDataTable.set(accountUUID, AccountDataTable.Key.MULTI_DEVICE, devices.size() > 0);
    return devices;
  }
}
