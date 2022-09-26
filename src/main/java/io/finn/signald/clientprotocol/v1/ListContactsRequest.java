/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

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
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;

@ProtocolType("list_contacts")
public class ListContactsRequest implements RequestType<ProfileList> {
  private static final Logger logger = LogManager.getLogger();

  @Required @ExampleValue(ExampleValue.LOCAL_UUID) public String account;

  @Doc("return results from local store immediately, refreshing from server afterward if needed. If false (default), block until all pending profiles have been retrieved.")
  public boolean async;

  @Override
  public ProfileList run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, AuthorizationFailedError, SQLError, InvalidRequestError, NetworkError {
    Account a = Common.getAccount(account);
    ProfileList list = new ProfileList();
    Database db = a.getDB();

    ArrayList<IContactsTable.ContactInfo> contacts;
    try {
      contacts = db.ContactsTable.getAll();
    } catch (SQLException e) {
      throw new SQLError(e);
    }

    for (var c : contacts) {
      IProfilesTable.Profile profile;
      try {
        profile = db.ProfilesTable.get(c.recipient);
      } catch (SQLException e) {
        throw new SQLError(e);
      }

      if (profile == null) {
        // no local profile, return details just from contact list
        list.profiles.add(new Profile(c));
        continue;
      }

      RefreshProfileJob action = new RefreshProfileJob(a, c.recipient);
      if (async) {
        BackgroundJobRunnerThread.queue(action);
      } else {
        try {
          action.run();
        } catch (NotFoundException | AuthorizationFailedException | InvalidKeyException e) {
          logger.warn("error refreshing profile:", e);
        } catch (NoSuchAccountException e) {
          throw new NoSuchAccountError(e);
        } catch (ServerNotFoundException e) {
          throw new ServerNotFoundError(e);
        } catch (InvalidProxyException e) {
          throw new InvalidProxyError(e);
        } catch (UnknownHostException e) {
          throw new NetworkError(e);
        } catch (SQLException | IOException e) {
          throw new InternalError("error refreshing profile", e);
        }
      }

      try {
        var recipient = Common.getRecipient(db.RecipientsTable, c.recipient.getAddress());
        Profile p = new Profile(db, recipient, c);
        p.populateAvatar(a);
        list.profiles.add(p);
      } catch (UnregisteredUserError e) {
        logger.debug("ignoring unregistered user in contact list");
      }
    }
    return list;
  }
}
