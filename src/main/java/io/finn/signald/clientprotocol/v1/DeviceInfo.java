/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
