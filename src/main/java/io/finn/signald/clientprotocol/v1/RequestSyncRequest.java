/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.Empty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.SendSyncRequestJob;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

@Doc("Request other devices on the account send us their group list, syncable config and contact list.")
@ProtocolType("request_sync")
public class RequestSyncRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to use") @Required @JsonProperty("account") public String accountIdentifier;
  @Doc("request group sync (default true)") public boolean groups = true;
  @Doc("request configuration sync (default true)") public boolean configuration = true;
  @Doc("request contact sync (default true)") public boolean contacts = true;
  @Doc("request block list sync (default true)") public boolean blocked = true;
  @Doc("request storage service keys") public boolean keys = true;

  @Override
  public Empty run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UntrustedIdentityError, InvalidRequestError, AuthorizationFailedError, SQLError {
    Account account = Common.getAccount(accountIdentifier);

    if (groups) {
      sendSyncRequest(account, SignalServiceProtos.SyncMessage.Request.Type.GROUPS);
    }

    if (configuration) {
      sendSyncRequest(account, SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION);
    }

    if (contacts) {
      sendSyncRequest(account, SignalServiceProtos.SyncMessage.Request.Type.CONTACTS);
    }

    if (blocked) {
      sendSyncRequest(account, SignalServiceProtos.SyncMessage.Request.Type.BLOCKED);
    }

    if (keys) {
      sendSyncRequest(account, SignalServiceProtos.SyncMessage.Request.Type.KEYS);
    }

    return new Empty();
  }

  private void sendSyncRequest(Account account, SignalServiceProtos.SyncMessage.Request.Type type)
      throws ServerNotFoundError, InvalidProxyError, NoSuchAccountError, InternalError, UntrustedIdentityError, AuthorizationFailedError {
    try {
      new SendSyncRequestJob(account, type).run();
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException | IOException | InvalidKeyException e) {
      throw new InternalError("error sending sync request", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (UntrustedIdentityException e) {
      throw new UntrustedIdentityError(account.getACI(), e);
    }
  }
}
