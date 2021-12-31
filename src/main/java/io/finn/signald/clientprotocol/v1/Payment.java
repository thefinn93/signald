/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidBase64Error;
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
  public SignalServiceDataMessage.PaymentNotification getPaymentNotification() throws InvalidBase64Error {
    try {
      return new SignalServiceDataMessage.PaymentNotification(Base64.decode(receipt), note);
    } catch (IOException e) {
      throw new InvalidBase64Error();
    }
  }
}
