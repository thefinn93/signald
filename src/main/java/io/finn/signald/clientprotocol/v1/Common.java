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
import io.finn.signald.SignalDependencies;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSendPermissionException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.AccountData;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

/* Common is a collection of wrapper functions that call common functions
 * and convert their exceptions to documented v1 exceptions
 */
public class Common {
  static Manager getManager(String identifier) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    if (identifier.startsWith("+")) {
      UUID accountID;
      try {
        accountID = AccountsTable.getUUID(identifier);
      } catch (io.finn.signald.exceptions.NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (SQLException e) {
        throw new InternalError("error getting manager", e);
      }
      return getManager(accountID);
    } else {
      return getManager(UUID.fromString(identifier));
    }
  }

  public static Manager getManager(UUID account) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    Manager m;
    try {
      m = Manager.get(account);
    } catch (io.finn.signald.exceptions.NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (IOException | SQLException | InvalidKeyException e) {
      throw new InternalError("error getting manager", e);
    }
    return m;
  }

  static void saveAccount(AccountData a) throws InternalError {
    try {
      a.save();
    } catch (IOException e) {
      throw new InternalError("error saving state to disk", e);
    }
  }

  static Recipient getRecipient(RecipientsTable table, SignalServiceAddress address) throws InternalError {
    try {
      return table.get(address);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  static List<Recipient> getRecipient(RecipientsTable table, List<SignalServiceAddress> addresses) throws InternalError {
    try {
      return table.get(addresses);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  static Recipient getRecipient(RecipientsTable table, JsonAddress address) throws InternalError {
    try {
      return table.get(address);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  public static Recipient getRecipient(RecipientsTable table, String address) throws InternalError {
    try {
      return table.get(address);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  public static List<SendMessageResult> send(Manager manager, SignalServiceDataMessage.Builder messageBuilder, Recipient recipient, String recipientGroupId)
      throws InvalidRecipientError, UnknownGroupError, NoSendPermissionError, InternalError {
    try {
      return manager.send(messageBuilder, recipient, recipientGroupId);
    } catch (io.finn.signald.exceptions.InvalidRecipientException e) {
      throw new InvalidRecipientError();
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (NoSendPermissionException e) {
      throw new NoSendPermissionError();
    } catch (IOException | SQLException e) {
      throw new InternalError("error sending message", e);
    }
  }

  public static UUID getAccount(String identifier) throws NoSuchAccountError, InternalError {
    try {
      return AccountsTable.getUUID(identifier);
    } catch (SQLException e) {
      throw new InternalError("error looking up local account", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }
  }

  public static SignalDependencies getDependencies(UUID accountUUID) throws InvalidProxyError, ServerNotFoundError, InternalError {
    try {
      return SignalDependencies.get(accountUUID);
    } catch (SQLException | IOException e) {
      throw new InternalError("error reading local account state", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }
  }
}
