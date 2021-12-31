/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
