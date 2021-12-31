/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import org.whispersystems.util.Base64;

public class AnswerMessage {
  public final long id;
  public final String sdp;
  public final String opaque;

  public AnswerMessage(org.whispersystems.signalservice.api.messages.calls.AnswerMessage message) {
    id = message.getId();
    sdp = message.getSdp();
    opaque = Base64.encodeBytes(message.getOpaque());
  }
}
