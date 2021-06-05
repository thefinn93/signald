package io.finn.signald.db;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class StoredEnvelope {
  public final long databaseId;
  public final SignalServiceEnvelope envelope;

  StoredEnvelope(long databaseId, SignalServiceEnvelope envelope) {
    this.databaseId = databaseId;
    this.envelope = envelope;
  }
}
