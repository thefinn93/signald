/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;

public class DeviceInfo {
  private static final Logger logger = LogManager.getLogger();

  public long id;
  public String name;
  public long created;
  public long lastSeen;

  public DeviceInfo(org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo d, ECPrivateKey profileKey) {
    id = d.getId();
    try {
      name = DeviceNameUtil.decryptDeviceName(d.getName(), profileKey);
    } catch (IOException e) {
      logger.warn("error decrypting name of linked device: " + e.getMessage());
    }
    created = d.getCreated();
    lastSeen = d.getLastSeen();
  }
}
