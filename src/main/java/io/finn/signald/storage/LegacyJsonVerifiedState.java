/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
@Deprecated
public class LegacyJsonVerifiedState {
  public LegacyIdentityKeyStore.Identity identity;
  public long timestamp;
  public String state;

  public LegacyJsonVerifiedState() {}

  public LegacyJsonVerifiedState(VerifiedMessage verifiedMessage) {
    identity = new LegacyIdentityKeyStore.Identity(verifiedMessage.getIdentityKey());
    timestamp = verifiedMessage.getTimestamp();
    state = verifiedMessage.getVerified().name();
  }
}
