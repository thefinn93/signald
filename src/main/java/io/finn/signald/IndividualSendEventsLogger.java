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

import io.finn.signald.db.Recipient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

// a basic class that logs individual send events
public class IndividualSendEventsLogger implements SignalServiceMessageSender.IndividualSendEvents {
  private final Logger logger;

  public IndividualSendEventsLogger(Recipient r) { logger = LogManager.getLogger("send-to-" + r.toRedactedString()); }

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
