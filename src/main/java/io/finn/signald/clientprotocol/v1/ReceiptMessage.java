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
