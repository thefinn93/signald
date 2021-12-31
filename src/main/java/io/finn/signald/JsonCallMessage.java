/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import java.util.List;
import org.whispersystems.signalservice.api.messages.calls.*;

@Deprecated(1641027661)
public class JsonCallMessage {
  public OfferMessage offerMessage;
  public AnswerMessage answerMessage;
  public BusyMessage busyMessage;
  public HangupMessage hangupMessage;
  public List<IceUpdateMessage> iceUpdateMessages;
  public int destinationDeviceId;
  public boolean isMultiRing;

  public JsonCallMessage(SignalServiceCallMessage callMessage) {
    if (callMessage.getOfferMessage().isPresent()) {
      offerMessage = callMessage.getOfferMessage().get();
    }

    if (callMessage.getAnswerMessage().isPresent()) {
      answerMessage = callMessage.getAnswerMessage().get();
    }

    if (callMessage.getBusyMessage().isPresent()) {
      busyMessage = callMessage.getBusyMessage().get();
    }

    if (callMessage.getHangupMessage().isPresent()) {
      hangupMessage = callMessage.getHangupMessage().get();
    }

    if (callMessage.getIceUpdateMessages().isPresent()) {
      iceUpdateMessages = callMessage.getIceUpdateMessages().get();
    }

    if (callMessage.getDestinationDeviceId().isPresent()) {
      destinationDeviceId = callMessage.getDestinationDeviceId().get();
    }

    isMultiRing = callMessage.isMultiRing();
  }
}
