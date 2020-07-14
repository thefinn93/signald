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

package io.finn.signald;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.util.SafetyNumberHelper;
import org.asamk.signal.util.Hex;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;


class JsonUntrustedIdentityException {
  public JsonAddress address;
  public JsonAddress number;
  public String fingerprint;
  public String safety_number;
  public JsonRequest request;

  JsonUntrustedIdentityException(IdentityKey key, SignalServiceAddress address, Manager m, JsonRequest request) {
    this.address = new JsonAddress(m.getOwnAddress());
    this.number = new JsonAddress(address);
    this.fingerprint = Hex.toStringCondensed(key.getPublicKey().serialize());
    this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getOwnAddress(), m.getIdentity(), this.address.getSignalServiceAddress(), key);
    this.request = request;
  }
}
