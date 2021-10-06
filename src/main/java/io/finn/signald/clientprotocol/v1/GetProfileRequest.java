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
  public Profile run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, ProfileUnavailableError {
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
      }
    }

    Profile profile = new Profile(profileEntry.getProfile(), recipient, contact);
    profile.populateAvatar(m);
    return profile;
  }
}
