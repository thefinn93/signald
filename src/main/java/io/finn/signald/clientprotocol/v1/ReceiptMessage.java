/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

public class ReceiptMessage {
  @Doc("options: UNKNOWN, DELIVERY, READ, VIEWED") public String type;
  public List<Long> timestamps;
  public long when;

  public ReceiptMessage(SignalServiceReceiptMessage receiptMessage) {
    type = receiptMessage.getType().name();
    timestamps = receiptMessage.getTimestamps();
    when = receiptMessage.getWhen();
  }
}
