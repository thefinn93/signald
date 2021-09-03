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

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

@Deprecated(1641027661)
public class JsonSentTranscriptMessage {
  public JsonAddress destination;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;
  public long expirationStartTimestamp;
  public JsonDataMessage message;
  public Map<String, Boolean> unidentifiedStatus = new HashMap<>();
  public boolean isRecipientUpdate;

  JsonSentTranscriptMessage(SentTranscriptMessage s, UUID accountUUID)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    if (s.getDestination().isPresent()) {
      destination = new JsonAddress(s.getDestination().get());
    }
    timestamp = s.getTimestamp();
    expirationStartTimestamp = s.getExpirationStartTimestamp();
    message = new JsonDataMessage(s.getMessage(), accountUUID);
    for (SignalServiceAddress r : s.getRecipients()) {
      if (r.getNumber().isPresent()) {
        unidentifiedStatus.put(r.getNumber().get(), s.isUnidentified(r.getNumber().get()));
      }
      unidentifiedStatus.put(r.getUuid().toString(), s.isUnidentified(r.getUuid()));
    }
    isRecipientUpdate = s.isRecipientUpdate();
  }
}
