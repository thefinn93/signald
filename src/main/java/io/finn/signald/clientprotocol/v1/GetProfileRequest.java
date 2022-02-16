/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Doc("Get all information available about a user")
@ProtocolType("get_profile")
public class GetProfileRequest implements RequestType<Profile> {

  @Required @Doc("the signald account to use") public String account;

  @Required @Doc("the address to look up") @JsonProperty("address") public JsonAddress requestedAddress;

  @Doc("if true, return results from local store immediately, refreshing from server in the background if needed. "
       + "if false (default), block until profile can be retrieved from server")
  public boolean async;

  @Override
  public Profile run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, ProfileUnavailableError, UnregisteredUserError {
    Manager m = Common.getManager(account);
    Recipient recipient = Common.getRecipient(m.getRecipientsTable(), requestedAddress);
    ContactStore.ContactInfo contact = m.getAccountData().contactStore.getContact(recipient);
    ProfileAndCredentialEntry profileEntry = m.getAccountData().profileCredentialStore.get(recipient);
    if (profileEntry == null) {
      if (contact == null) {
        throw new ProfileUnavailableError();
      } else {
        Profile p = new Profile(contact);
        p.populateAvatar(m);
        return p;
      }
    }

    RefreshProfileJob action = new RefreshProfileJob(m, profileEntry);
    if (async) {
      BackgroundJobRunnerThread.queue(action);
    } else {
      try {
        action.run();
      } catch (InterruptedException | ExecutionException | TimeoutException | IOException | SQLException e) {
        throw new InternalError("error refreshing profile", e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
    }

    Profile profile = new Profile(profileEntry.getProfile(), recipient, contact);
    profile.populateAvatar(m);
    return profile;
  }
}
