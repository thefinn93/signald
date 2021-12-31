/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
