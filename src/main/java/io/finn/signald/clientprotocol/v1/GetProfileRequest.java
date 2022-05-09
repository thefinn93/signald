/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IContactsTable;
import io.finn.signald.db.IProfilesTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.libsignal.protocol.InvalidKeyException;

@Doc("Get all information available about a user")
@ProtocolType("get_profile")
public class GetProfileRequest implements RequestType<Profile> {

  @ExampleValue(ExampleValue.LOCAL_UUID) @Required @Doc("the signald account to use") public String account;

  @Required @Doc("the address to look up") @JsonProperty("address") public JsonAddress requestedAddress;

  @Doc("if true, return results from local store immediately, refreshing from server in the background if needed. "
       + "if false (default), block until profile can be retrieved from server")
  public boolean async;

  @Override
  public Profile run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, ProfileUnavailableError, UnregisteredUserError,
                                             AuthorizationFailedError, SQLError, InvalidRequestError {
    Account a = Common.getAccount(account);
    Recipient recipient = Common.getRecipient(a.getACI(), requestedAddress);

    IProfilesTable.Profile profile;
    try {
      profile = a.getDB().ProfilesTable.get(recipient);
    } catch (SQLException e) {
      throw new SQLError(e);
    }

    IContactsTable.ContactInfo contact;
    try {
      contact = Database.Get(a.getACI()).ContactsTable.get(recipient);
    } catch (SQLException e) {
      throw new SQLError(e);
    }

    if (profile == null) {
      if (contact == null) {
        throw new ProfileUnavailableError();
      }
      Profile p = new Profile(contact);
      p.populateAvatar(a);
      return p;
    }

    RefreshProfileJob action = new RefreshProfileJob(a, recipient);
    if (async) {
      BackgroundJobRunnerThread.queue(action);
    } else {
      try {
        action.run();
      } catch (InvalidKeyException | IOException e) {
        throw new InternalError("error refreshing profile", e);
      } catch (SQLException e) {
        throw new SQLError(e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
    }

    Profile p = new Profile(a.getDB(), recipient, contact);
    p.populateAvatar(a);
    return p;
  }
}
