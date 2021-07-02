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
