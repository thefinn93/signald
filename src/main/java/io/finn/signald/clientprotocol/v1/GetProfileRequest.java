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
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.ProfileUnavailable;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

@Doc("Get all information available about a user")
@SignaldClientRequest(type = "get_profile")
public class GetProfileRequest implements RequestType<Profile> {

  @Required @Doc("the signald account to use") public String account;

  @Required @Doc("the address to look up") @JsonProperty("address") public JsonAddress requestedAddress;

  @Doc("return results from local store immediately, refreshing from server if needed. If false (default), block until all pending profiles have been retrieved.")
  public boolean async;

  @Override
  public Profile run(Request request) throws Exception {
    Manager m = Manager.get(account);
    SignalServiceAddress address = m.getResolver().resolve(requestedAddress.getSignalServiceAddress());
    ContactStore.ContactInfo contact = m.getAccountData().contactStore.getContact(address);
    ProfileAndCredentialEntry profileEntry = m.getAccountData().profileCredentialStore.get(address);
    if (profileEntry == null) {
      if (contact == null) {
        throw new ProfileUnavailable();
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
      action.run();
    }

    Profile profile = new Profile(profileEntry.getProfile(), address, contact);
    profile.populateAvatar(m);
    return profile;
  }
}
