/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ProtocolType("list_contacts")
public class ListContactsRequest implements RequestType<ProfileList> {
  private static final Logger logger = LogManager.getLogger();

  @Required public String account;

  @Doc("return results from local store immediately, refreshing from server afterward if needed. If false (default), block until all pending profiles have been retrieved.")
  public boolean async;

  @Override
  public ProfileList run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    Manager m = Common.getManager(account);
    RecipientsTable recipientsTable = m.getRecipientsTable();
    ProfileList list = new ProfileList();
    for (ContactStore.ContactInfo c : m.getAccountData().contactStore.getContacts()) {
      ProfileAndCredentialEntry profileEntry = m.getAccountData().profileCredentialStore.get(c.address.getSignalServiceAddress());
      if (profileEntry == null) {
        list.profiles.add(new Profile(c));
        continue;
      }

      RefreshProfileJob action = new RefreshProfileJob(m, profileEntry);
      if (async) {
        BackgroundJobRunnerThread.queue(action);
      } else {
        try {
          action.run();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          logger.warn("error refreshing profile:", e);
        } catch (SQLException | IOException e) {
          throw new InternalError("error refreshing preofile", e);
        }
      }

      Recipient recipient = Common.getRecipient(recipientsTable, c.address);
      Profile profile = new Profile(profileEntry.getProfile(), recipient, c);
      profile.populateAvatar(m);
      list.profiles.add(profile);
    }
    return list;
  }
}
