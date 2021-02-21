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

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;

public class SendDeliveryReceiptJob implements Job {

  private final SignalServiceAddress to;
  private final List<Long> timestamps = new ArrayList<>();
  private final Manager m;

  public SendDeliveryReceiptJob(Manager manager, SignalServiceAddress address) {
    m = manager;
    to = address;
  }

  public SendDeliveryReceiptJob(Manager manager, SignalServiceAddress address, Long timestamp) {
    this(manager, address);
    addTimestamp(timestamp);
  }

  public void addTimestamp(Long timestamp) { timestamps.add(timestamp); }

  @Override
  public void run() throws Throwable {
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY, timestamps, System.currentTimeMillis());
    m.sendReceipt(message, to);
  }
}
