/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald.util;

import io.finn.signald.clientprotocol.v1.JsonAddress;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class AddressUtil {
  // Used to resolve local accounts
  private static final List<SignalServiceAddress> knownAddresses = new ArrayList<>();
  public static void addKnownAddress(SignalServiceAddress address) { knownAddresses.add(address); }

  public static SignalServiceAddress fromIdentifier(String identifier) {
    if (UuidUtil.isUuid(identifier)) {
      return new SignalServiceAddress(UuidUtil.parseOrNull(identifier));
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
