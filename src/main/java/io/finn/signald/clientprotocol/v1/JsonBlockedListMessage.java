/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

public class JsonBlockedListMessage {
  public List<JsonAddress> addresses;
  public List<String> groupIds;
  public JsonBlockedListMessage(BlockedListMessage blocklist) {
    if (!blocklist.getAddresses().isEmpty()) {
      addresses = new ArrayList<>();
      for (SignalServiceAddress a : blocklist.getAddresses()) {
        addresses.add(new JsonAddress(a));
      }
    }

    if (!blocklist.getGroupIds().isEmpty()) {
      groupIds = new ArrayList<>();
      for (byte[] groupId : blocklist.getGroupIds()) {
        groupIds.add(Base64.encodeBytes(groupId));
      }
    }
  }
}
