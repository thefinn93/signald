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
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.util.SafetyNumberHelper;
import org.asamk.signal.util.Hex;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.sql.SQLException;

class JsonUntrustedIdentityException {
  public JsonAddress local_address;
  public JsonAddress remote_address;
  public String fingerprint;
  public String safety_number;
  public JsonRequest request;

  JsonUntrustedIdentityException(IdentityKey key, SignalServiceAddress address, Manager m, JsonRequest request) {
    this.local_address = new JsonAddress(m.getOwnAddress());
    this.remote_address = new JsonAddress(address);
    this.fingerprint = Hex.toStringCondensed(key.getPublicKey().serialize());
    this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getOwnAddress(), m.getIdentity(), this.remote_address.getSignalServiceAddress(), key);
    this.request = request;
  }

  public JsonUntrustedIdentityException(UntrustedIdentityException exception, String username) {
    this.local_address = new JsonAddress(username);
    this.remote_address = new JsonAddress(exception.getName());
    this.fingerprint = Hex.toStringCondensed(exception.getUntrustedIdentity().getPublicKey().serialize());
    try {
      Manager m = Manager.get(username);
      this.local_address = new JsonAddress(m.getOwnAddress());
      this.safety_number =
          SafetyNumberHelper.computeSafetyNumber(m.getOwnAddress(), m.getIdentity(), this.remote_address.getSignalServiceAddress(), exception.getUntrustedIdentity());
    } catch (IOException | NoSuchAccountException | SQLException e) {
      e.printStackTrace();
    }
  }
}
