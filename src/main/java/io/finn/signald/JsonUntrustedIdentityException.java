/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.SafetyNumberHelper;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.util.Hex;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.UntrustedIdentityException;

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
        Recipient recipient = Database.Get(m.getACI()).RecipientsTable.get(this.remote_address);
        this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getOwnRecipient(), m.getIdentity(), recipient, exception.getUntrustedIdentity());
      }
    } catch (IOException | NoSuchAccountException | SQLException | InvalidKeyException | ServerNotFoundException | InvalidProxyException e) {
      logger.error("error preparing untrusted identity exception", e);
      Sentry.captureException(e);
    }
  }
}
