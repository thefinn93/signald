/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.clientprotocol.v1.Common;
import io.finn.signald.clientprotocol.v1.IdentityKey;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.util.SafetyNumberHelper;
import java.util.UUID;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

public class UntrustedIdentityError extends ExceptionWrapper {
  public String identifier;
  @JsonProperty("identity_key") public IdentityKey identityKey;

  UntrustedIdentityError(UUID accountUUID, String identifier, org.whispersystems.libsignal.IdentityKey libsignalIdentityKey)
      throws InternalError, ServerNotFoundError, InvalidProxyError, NoSuchAccountError {
    this.identifier = identifier;
    Manager m = Common.getManager(accountUUID);
    Recipient recipient = Common.getRecipient(new RecipientsTable(accountUUID), identifier);
    if (libsignalIdentityKey != null) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(m.getOwnRecipient(), m.getIdentity(), recipient, libsignalIdentityKey);
      if (fingerprint != null) {
        identityKey = new IdentityKey("UNTRUSTED", fingerprint);
      }
    }
  }

  public UntrustedIdentityError(UUID accountUUID, UntrustedIdentityException e) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    this(accountUUID, e.getIdentifier(), e.getIdentityKey());
  }

  public UntrustedIdentityError(UUID accountUUID, org.whispersystems.libsignal.UntrustedIdentityException e)
      throws InternalError, ServerNotFoundError, InvalidProxyError, NoSuchAccountError {
    this(accountUUID, e.getName(), e.getUntrustedIdentity());
  }
}
