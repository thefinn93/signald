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
