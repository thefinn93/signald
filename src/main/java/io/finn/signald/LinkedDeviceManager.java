/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

public class LinkedDeviceManager {
  private final Account account;

  public LinkedDeviceManager(Account account) { this.account = account; }

  public List<DeviceInfo> getLinkedDevices() throws IOException, SQLException, ServerNotFoundException, InvalidProxyException, NoSuchAccountException {
    List<DeviceInfo> devices = account.getSignalDependencies().getAccountManager().getDevices();
    account.setMultiDevice(devices.size() > 0);
    return devices;
  }
}
