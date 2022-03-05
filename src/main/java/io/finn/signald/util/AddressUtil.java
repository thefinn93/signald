/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

import io.finn.signald.clientprotocol.v1.JsonAddress;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class AddressUtil {
  // Used to resolve local accounts
  private static final List<SignalServiceAddress> knownAddresses = new ArrayList<>();
  public static void addKnownAddress(SignalServiceAddress address) { knownAddresses.add(address); }

  public static SignalServiceAddress fromIdentifier(String identifier) {
    if (UuidUtil.isUuid(identifier)) {
      return new SignalServiceAddress(ACI.from(UuidUtil.parseOrNull(identifier)));
    } else {
      return new SignalServiceAddress(null, identifier);
    }
  }
}
