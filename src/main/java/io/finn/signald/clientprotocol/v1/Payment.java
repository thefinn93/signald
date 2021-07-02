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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import java.io.IOException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.util.Base64;

@Doc("details about a MobileCoin payment")
public class Payment {

  @Doc("base64 encoded payment receipt data. This is a protobuf value which can be decoded as the Receipt object "
       + "described in https://github.com/mobilecoinfoundation/mobilecoin/blob/master/api/proto/external.proto")
  public String receipt;
  @Doc("note attached to the payment") public String note;

  Payment() {}

  public Payment(SignalServiceDataMessage.PaymentNotification p) {
    receipt = Base64.encodeBytes(p.getReceipt());
    note = p.getNote();
  }

  @JsonIgnore
  public SignalServiceDataMessage.PaymentNotification getPaymentNotification() throws IOException {
    return new SignalServiceDataMessage.PaymentNotification(Base64.decode(receipt), note);
  }
}
