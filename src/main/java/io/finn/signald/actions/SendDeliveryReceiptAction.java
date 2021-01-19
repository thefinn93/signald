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

package io.finn.signald.actions;

import io.finn.signald.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;

public class SendDeliveryReceiptAction implements Action {

  private SignalServiceAddress to;
  private List<Long> timestamps = new ArrayList<>();

  public SendDeliveryReceiptAction(SignalServiceAddress address) { to = address; }

  public SendDeliveryReceiptAction(SignalServiceAddress address, Long timestamp) {
    this(address);
    addTimestamp(timestamp);
  }

  public void addTimestamp(Long timestamp) { timestamps.add(timestamp); }

  @Override
  public String getName() {
    return SendDeliveryReceiptAction.class.getName();
  }

  @Override
  public void run(Manager m) throws Throwable {
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY, timestamps, System.currentTimeMillis());
    m.sendReceipt(message, to);
  }
}
