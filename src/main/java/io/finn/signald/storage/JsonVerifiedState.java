/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

public class JsonVerifiedState {
  public IdentityKeyStore.Identity identity;
  public long timestamp;
  public String state;

  public JsonVerifiedState() {}

  public JsonVerifiedState(VerifiedMessage verifiedMessage) {
    identity = new IdentityKeyStore.Identity(verifiedMessage.getIdentityKey());
    timestamp = verifiedMessage.getTimestamp();
    state = verifiedMessage.getVerified().name();
  }
}
