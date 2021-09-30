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

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.SafetyNumberHelper;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.util.Hex;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.UntrustedIdentityException;

@Deprecated(1641027661)
class JsonUntrustedIdentityException {
  private static final Logger logger = LogManager.getLogger();
  public JsonAddress local_address;
  public JsonAddress remote_address;
  public String fingerprint;
  public String safety_number;
  public JsonRequest request;

  JsonUntrustedIdentityException(IdentityKey key, Recipient recipient, Manager m, JsonRequest request) {
    this.local_address = new JsonAddress(m.getOwnRecipient());
    this.remote_address = new JsonAddress(recipient);
    this.fingerprint = Hex.toStringCondensed(key.getPublicKey().serialize());
    this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getOwnRecipient(), m.getIdentity(), recipient, key);
    this.request = request;
  }

  public JsonUntrustedIdentityException(UntrustedIdentityException exception, String username) {
    this.local_address = new JsonAddress(username);
    this.remote_address = new JsonAddress(exception.getName());
    if (exception.getUntrustedIdentity() != null) {
      this.fingerprint = Hex.toStringCondensed(exception.getUntrustedIdentity().getPublicKey().serialize());
    }
    try {
      Manager m = Manager.get(username);
      this.local_address = new JsonAddress(m.getOwnRecipient());
      if (exception.getUntrustedIdentity() != null) {
        Recipient recipient = m.getRecipientsTable().get(this.remote_address);
        this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getOwnRecipient(), m.getIdentity(), recipient, exception.getUntrustedIdentity());
      }
    } catch (IOException | NoSuchAccountException | SQLException | InvalidKeyException | ServerNotFoundException | InvalidProxyException e) {
      logger.error("error preparing untrusted identity exception", e);
    }
  }
}
