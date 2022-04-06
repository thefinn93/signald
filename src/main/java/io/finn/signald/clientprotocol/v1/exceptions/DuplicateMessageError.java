/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class DuplicateMessageError extends ExceptionWrapper {
  public long timestamp;
  public DuplicateMessageError(SignalServiceEnvelope envelope, DuplicateMessageException e) {
    super(e);
    timestamp = envelope.getTimestamp();
  }
}
