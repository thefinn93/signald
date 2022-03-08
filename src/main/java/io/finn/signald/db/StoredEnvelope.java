/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class StoredEnvelope {
  public final long databaseId;
  public final SignalServiceEnvelope envelope;

  public StoredEnvelope(long databaseId, SignalServiceEnvelope envelope) {
    this.databaseId = databaseId;
    this.envelope = envelope;
  }
}
