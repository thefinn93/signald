/*
 * Copyright (C) 2020 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald;

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.thoughtcrime.securesms.util.Hex;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

class JsonSticker {
  private static final Logger logger = LogManager.getLogger();

  String packID;
  String packKey;
  int stickerID;
  JsonAttachment attachment;

  JsonSticker(SignalServiceDataMessage.Sticker sticker, String username) throws IOException, NoSuchAccountException {
    packID = Hex.toStringCondensed(sticker.getPackId());
    packKey = Hex.toStringCondensed(sticker.getPackKey());
    stickerID = sticker.getStickerId();
    attachment = new JsonAttachment(sticker.getAttachment(), username);
  }
}
