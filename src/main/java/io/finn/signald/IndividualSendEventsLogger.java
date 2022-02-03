/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.db.Recipient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

// a basic class that logs individual send events
public class IndividualSendEventsLogger implements SignalServiceMessageSender.IndividualSendEvents {
  private final static Logger logger = LogManager.getLogger();
  public final static IndividualSendEventsLogger INSTANCE = new IndividualSendEventsLogger();

  @Override
  public void onMessageEncrypted() {
    logger.debug("message encrypted");
  }

  @Override
  public void onMessageSent() {
    logger.debug("message sent");
  }

  @Override
  public void onSyncMessageSent() {
    logger.debug("sync message sent");
  }
}
