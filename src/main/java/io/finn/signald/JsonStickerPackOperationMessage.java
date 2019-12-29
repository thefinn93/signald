/**
 * Copyright (C) 2019 Finn Herzfeld
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

import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.thoughtcrime.securesms.util.Hex;

class JsonStickerPackOperationMessage {
  String packID;
  String packKey;
  String type;

  JsonStickerPackOperationMessage(StickerPackOperationMessage message) {
    if(message.getPackId().isPresent()) {
      packID = Hex.toStringCondensed(message.getPackId().get()); 
    }

    if(message.getPackKey().isPresent()) {
      packKey = Hex.toStringCondensed(message.getPackKey().get());
    }

    if(message.getType().isPresent()) {
      type = message.getType().get().toString();
    }
  }
}
