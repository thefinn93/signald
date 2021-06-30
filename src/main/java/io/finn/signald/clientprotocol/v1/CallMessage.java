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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;

public class CallMessage {
  @JsonProperty("offer_message") public OfferMessage offerMessage;
  @JsonProperty("answer_message") public AnswerMessage answerMessage;
  @JsonProperty("busy_message") public BusyMessage busyMessage;
  @JsonProperty("hangup_message") public HangupMessage hangupMessage;
  @JsonProperty("ice_update_message") public List<IceUpdateMessage> iceUpdateMessages;
  @JsonProperty("destination_device_id") public Integer destinationDeviceId;
  @JsonProperty("multi_ring") public boolean isMultiRing;

  public CallMessage(SignalServiceCallMessage callMessage) {
    if (callMessage.getOfferMessage().isPresent()) {
      offerMessage = new OfferMessage(callMessage.getOfferMessage().get());
    }

    if (callMessage.getAnswerMessage().isPresent()) {
      answerMessage = new AnswerMessage(callMessage.getAnswerMessage().get());
    }

    if (callMessage.getBusyMessage().isPresent()) {
      busyMessage = new BusyMessage(callMessage.getBusyMessage().get());
    }

    if (callMessage.getHangupMessage().isPresent()) {
      hangupMessage = new HangupMessage(callMessage.getHangupMessage().get());
    }

    if (callMessage.getIceUpdateMessages().isPresent()) {
      iceUpdateMessages = callMessage.getIceUpdateMessages().get().stream().map(IceUpdateMessage::new).collect(Collectors.toList());
    }

    if (callMessage.getDestinationDeviceId().isPresent()) {
      destinationDeviceId = callMessage.getDestinationDeviceId().get();
    }

    isMultiRing = callMessage.isMultiRing();
  }
}
