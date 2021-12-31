/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import org.whispersystems.util.Base64;

public class IceUpdateMessage {
  public final long id;
  public final String opaque;
  public final String sdp;

  public IceUpdateMessage(org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage message) {
    id = message.getId();
    opaque = Base64.encodeBytes(message.getOpaque());
    sdp = message.getSdp();
  }
}
