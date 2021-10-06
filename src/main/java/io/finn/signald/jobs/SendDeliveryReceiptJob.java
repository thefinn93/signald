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
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

public class SendDeliveryReceiptJob implements Job {

  private final Recipient recipient;
  private final List<Long> timestamps = new ArrayList<>();
  private final Manager m;

  public SendDeliveryReceiptJob(Manager manager, Recipient address) {
    m = manager;
    recipient = address;
  }

  public SendDeliveryReceiptJob(Manager manager, Recipient address, Long timestamp) {
    this(manager, address);
    addTimestamp(timestamp);
  }

  public void addTimestamp(Long timestamp) { timestamps.add(timestamp); }

  @Override
  public void run() throws IOException, SQLException {
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY, timestamps, System.currentTimeMillis());
    m.sendReceipt(message, recipient);
  }
}
