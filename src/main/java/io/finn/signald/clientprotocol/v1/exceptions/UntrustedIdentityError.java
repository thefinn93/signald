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

  public UntrustedIdentityError(UUID accountUUID, UntrustedIdentityException e) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    Manager m = Common.getManager(accountUUID);
    Recipient recipient = Common.getRecipient(new RecipientsTable(accountUUID), e.getIdentifier());
    Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(m.getOwnRecipient(), m.getIdentity(), recipient, e.getIdentityKey());
    identityKey = new IdentityKey("UNTRUSTED", fingerprint);
  }
}
