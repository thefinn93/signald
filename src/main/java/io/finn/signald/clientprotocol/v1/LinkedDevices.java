/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.List;

public class LinkedDevices {
  public List<DeviceInfo> devices;
  LinkedDevices(List<DeviceInfo> devices) { this.devices = devices; }
}
