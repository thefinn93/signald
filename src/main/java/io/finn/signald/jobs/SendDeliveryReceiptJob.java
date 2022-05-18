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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

public class SendDeliveryReceiptJob implements Job {
  private static final Logger logger = LogManager.getLogger();

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
    try {
      m.sendReceipt(message, recipient);
    } catch (UnregisteredUserException e) {
      logger.debug("tried to send a receipt to an unregistered user {}", recipient.toRedactedString());
    } catch (ProofRequiredException e) {
      logger.warn("ProofRequiredException while sending delivery receipt job to {}", recipient.toRedactedString());
    }
  }
}
