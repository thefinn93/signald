/*
 * Copyright (C) 2021 Finn Herzfeld
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

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.IOException;
import java.sql.SQLException;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Deprecated(1641027661)
public class JsonSticker {
  public String packID;
  public String packKey;
  public int stickerID;
  public JsonAttachment attachment;

  public JsonSticker(SignalServiceDataMessage.Sticker sticker, String username) throws IOException, NoSuchAccountException, SQLException {
    packID = Hex.toStringCondensed(sticker.getPackId());
    packKey = Hex.toStringCondensed(sticker.getPackKey());
    stickerID = sticker.getStickerId();
    attachment = new JsonAttachment(sticker.getAttachment(), username);
  }
}
