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

import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.actions.Action;
import io.finn.signald.actions.RefreshProfileAction;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SignaldClientRequest(type = "list_contacts", ResponseClass = ProfileList.class)
public class ListContactsRequest implements RequestType {
  private static final Logger logger = LogManager.getLogger();

  @Required public String account;

  @Doc("return results from local store immediately, refreshing from server if needed. If false (default), block until all pending profiles have been retrieved.")
  public boolean async;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(account);
    ProfileList list = new ProfileList();
    List<Action> actions = new ArrayList<>();
    for (ContactStore.ContactInfo c : m.getAccountData().contactStore.getContacts()) {
      ProfileAndCredentialEntry profileEntry = m.getAccountData().profileCredentialStore.get(c.address.getSignalServiceAddress());
      if (profileEntry == null) {
        continue;
      }

      RefreshProfileAction action = new RefreshProfileAction(profileEntry);
      if (async) {
        actions.add(action);
      } else {
        try {
          action.run(m);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          logger.warn("error refreshing profile:", e);
        }
      }

      Profile profile = new Profile(profileEntry.getProfile(), c.address.getSignalServiceAddress(), c);
      list.profiles.add(profile);
    }
    request.reply(list);
    for (Action action : actions) {
      try {
        logger.debug("running " + action.getName());
        action.run(m);
      } catch (Throwable t) {
        logger.warn("Error running " + action.getName());
        logger.catching(t);
      }
    }
  }
}
