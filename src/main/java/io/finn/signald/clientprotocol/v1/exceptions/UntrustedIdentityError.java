/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.clientprotocol.v1.Common;
import io.finn.signald.clientprotocol.v1.IdentityKey;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.util.SafetyNumberHelper;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.ACI;

public class UntrustedIdentityError extends ExceptionWrapper {
  public String identifier;
  @JsonProperty("identity_key") public IdentityKey identityKey;

  UntrustedIdentityError(ACI aci, String identifier, org.whispersystems.libsignal.IdentityKey libsignalIdentityKey)
      throws InternalError, ServerNotFoundError, InvalidProxyError, NoSuchAccountError, AuthorizationFailedError {
    this.identifier = identifier;
    Manager m = Common.getManager(aci);
    Recipient recipient = Common.getRecipient(new RecipientsTable(aci), identifier);
    if (libsignalIdentityKey != null) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(m.getOwnRecipient(), m.getIdentity(), recipient, libsignalIdentityKey);
      if (fingerprint != null) {
        identityKey = new IdentityKey("UNTRUSTED", fingerprint);
      }
    }
  }

  public UntrustedIdentityError(ACI aci, UntrustedIdentityException e) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, AuthorizationFailedError {
    this(aci, e.getIdentifier(), e.getIdentityKey());
  }

  public UntrustedIdentityError(ACI aci, org.whispersystems.libsignal.UntrustedIdentityException e)
      throws InternalError, ServerNotFoundError, InvalidProxyError, NoSuchAccountError, AuthorizationFailedError {
    this(aci, e.getName(), e.getUntrustedIdentity());
  }
}
