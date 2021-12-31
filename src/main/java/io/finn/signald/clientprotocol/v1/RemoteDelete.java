/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class RemoteDelete {
  @JsonProperty("target_sent_timestamp") public final long targetSentTimestamp;

  public RemoteDelete(SignalServiceDataMessage.RemoteDelete r) { this.targetSentTimestamp = r.getTargetSentTimestamp(); }
}
