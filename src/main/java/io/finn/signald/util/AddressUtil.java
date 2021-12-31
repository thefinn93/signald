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

  public static JsonAddress update(JsonAddress old, JsonAddress update) {
    assert old.matches(update);
    JsonAddress result = new JsonAddress(old.getSignalServiceAddress());
    if (update.number != null) {
      result.number = update.number;
    }
    if (update.uuid != null) {
      result.uuid = update.uuid;
    }
    return old;
  }

  public SignalServiceAddress resolve(SignalServiceAddress partial) {
    for (SignalServiceAddress k : knownAddresses) {
      if (k.matches(partial)) {
        return k;
      }
    }
    return partial;
  }
}
