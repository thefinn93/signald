/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

@Doc("Request other devices on the account send us their group list, syncable config and contact list.")
@ProtocolType("request_sync")
public class RequestSyncRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @Doc("request group sync (default true)") public boolean groups = true;
  @Doc("request configuration sync (default true)") public boolean configuration = true;
  @Doc("request contact sync (default true)") public boolean contacts = true;
  @Doc("request block list sync (default true)") public boolean blocked = true;

  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UntrustedIdentityError {
    Manager m = Common.getManager(account);
    if (groups) {
      try {
        m.requestSyncGroups();
      } catch (IOException | SQLException e) {
        throw new InternalError("error requesting group sync", e);
      } catch (UntrustedIdentityException e) {
        throw new UntrustedIdentityError(m.getACI(), e);
      }
    }

    if (configuration) {
      try {
        m.requestSyncConfiguration();
      } catch (IOException | SQLException e) {
        throw new InternalError("error requesting config sync", e);
      } catch (UntrustedIdentityException e) {
        throw new UntrustedIdentityError(m.getACI(), e);
      }
    }

    if (contacts) {
      try {
        m.requestSyncContacts();
      } catch (IOException | SQLException e) {
        throw new InternalError("error requesting contacts sync", e);
      } catch (UntrustedIdentityException e) {
        throw new UntrustedIdentityError(m.getACI(), e);
      }
    }

    if (blocked) {
      try {
        m.requestSyncBlocked();
      } catch (IOException | SQLException e) {
        throw new InternalError("error requesting blocklist sync", e);
      } catch (UntrustedIdentityException e) {
        throw new UntrustedIdentityError(m.getACI(), e);
      }
    }

    return new Empty();
  }
}
