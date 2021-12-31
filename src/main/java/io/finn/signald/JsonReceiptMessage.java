/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;

@Deprecated(1641027661)
public class JsonReceiptMessage {
  public String type;
  public List<Long> timestamps;
  public long when;

  public JsonReceiptMessage(SignalServiceReceiptMessage receiptMessage) {
    type = receiptMessage.getType().name();
    timestamps = receiptMessage.getTimestamps();
    when = receiptMessage.getWhen();
  }
}
