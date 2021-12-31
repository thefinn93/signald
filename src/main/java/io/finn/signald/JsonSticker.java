/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Deprecated(1641027661)
public class JsonSticker {
  public String packID;
  public String packKey;
  public int stickerID;
  public JsonAttachment attachment;
  public String image;

  public JsonSticker(SignalServiceDataMessage.Sticker sticker) {
    packID = Hex.toStringCondensed(sticker.getPackId());
    packKey = Hex.toStringCondensed(sticker.getPackKey());
    stickerID = sticker.getStickerId();
    image = Manager.getStickerFile(sticker).getAbsolutePath();
    attachment = new JsonAttachment(sticker.getAttachment());
    attachment.storedFilename = image;
  }
}
