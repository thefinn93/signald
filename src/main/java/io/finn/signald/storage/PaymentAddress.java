/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

public class PaymentAddress {
  @JsonProperty private String address;

  private PaymentAddress() {}

  public PaymentAddress(SignalServiceProtos.PaymentAddress a) { address = Base64.encodeBytes(a.toByteArray()); }

  public SignalServiceProtos.PaymentAddress get() throws IOException { return SignalServiceProtos.PaymentAddress.parseFrom(Base64.decode(address)); }
}
