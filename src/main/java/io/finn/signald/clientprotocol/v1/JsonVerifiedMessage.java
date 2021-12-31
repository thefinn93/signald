/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import org.asamk.signal.util.Hex;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

public class JsonVerifiedMessage {
  public JsonAddress destination;
  public String identityKey;
  public String verified;
  public long timestamp;

  public JsonVerifiedMessage(VerifiedMessage message) {
    destination = new JsonAddress(message.getDestination());
    identityKey = Hex.toStringCondensed(message.getIdentityKey().getPublicKey().serialize());
    verified = message.getVerified().toString();
    timestamp = message.getTimestamp();
  }
}
