/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;

@Deprecated(1641027661)
class JsonConfigurationMessage {
  boolean readReceipts;
  boolean unidentifiedDeliveryIndicators;
  boolean typingIndicators;
  boolean linkPreviews;

  JsonConfigurationMessage(ConfigurationMessage verifiedMessage) {
    if (verifiedMessage.getReadReceipts().isPresent()) {
      readReceipts = verifiedMessage.getReadReceipts().get();
    }

    if (verifiedMessage.getUnidentifiedDeliveryIndicators().isPresent()) {
      unidentifiedDeliveryIndicators = verifiedMessage.getUnidentifiedDeliveryIndicators().get();
    }

    if (verifiedMessage.getTypingIndicators().isPresent()) {
      typingIndicators = verifiedMessage.getTypingIndicators().get();
    }

    if (verifiedMessage.getLinkPreviews().isPresent()) {
      linkPreviews = verifiedMessage.getLinkPreviews().get();
    }
  }
}
