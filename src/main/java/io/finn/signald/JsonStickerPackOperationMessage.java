/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;

@Deprecated(1641027661)
public class JsonStickerPackOperationMessage {
  public String packID;
  public String packKey;
  public String type;

  public JsonStickerPackOperationMessage(StickerPackOperationMessage message) {
    if (message.getPackId().isPresent()) {
      packID = Hex.toStringCondensed(message.getPackId().get());
    }

    if (message.getPackKey().isPresent()) {
      packKey = Hex.toStringCondensed(message.getPackKey().get());
    }

    if (message.getType().isPresent()) {
      type = message.getType().get().toString();
    }
  }
}
