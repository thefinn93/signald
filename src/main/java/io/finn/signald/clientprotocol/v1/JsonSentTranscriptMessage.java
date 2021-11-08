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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class JsonSentTranscriptMessage {
  public JsonAddress destination;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;
  public long expirationStartTimestamp;
  public JsonDataMessage message;
  public Map<String, Boolean> unidentifiedStatus = new HashMap<>();
  public boolean isRecipientUpdate;

  JsonSentTranscriptMessage(SentTranscriptMessage s, ACI aci) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    if (s.getDestination().isPresent()) {
      destination = new JsonAddress(s.getDestination().get());
    }
    timestamp = s.getTimestamp();
    expirationStartTimestamp = s.getExpirationStartTimestamp();
    message = new JsonDataMessage(s.getMessage(), aci);
    for (SignalServiceAddress r : s.getRecipients()) {
      if (r.getNumber().isPresent()) {
        unidentifiedStatus.put(r.getNumber().get(), s.isUnidentified(r.getNumber().get()));
      }
      unidentifiedStatus.put(r.getAci().toString(), s.isUnidentified(r.getAci()));
    }
    isRecipientUpdate = s.isRecipientUpdate();
  }
}
