/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.util.HashMap;
import java.util.Map;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class JsonSentTranscriptMessage {
  public JsonAddress destination;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;
  public long expirationStartTimestamp;
  public JsonDataMessage message;
  public StoryMessage story;
  public Map<String, String> unidentifiedStatus = new HashMap<>();
  public boolean isRecipientUpdate;

  JsonSentTranscriptMessage(SentTranscriptMessage s, ACI aci)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError, NetworkError {
    if (s.getDestination().isPresent()) {
      destination = new JsonAddress(s.getDestination().get());
    }
    timestamp = s.getTimestamp();
    expirationStartTimestamp = s.getExpirationStartTimestamp();
    if (s.getDataMessage().isPresent()) {
      message = new JsonDataMessage(s.getDataMessage().get(), aci);
    }
    if (s.getStoryMessage().isPresent()) {
      story = new StoryMessage(s.getStoryMessage().get(), aci);
    }
    for (ServiceId r : s.getRecipients()) {
      unidentifiedStatus.put(r.toString(), s.isUnidentified(r) ? "true" : "false");
    }
    isRecipientUpdate = s.isRecipientUpdate();
  }
}
