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

import io.finn.signald.BuildConfig;
import io.finn.signald.Manager;
import io.finn.signald.RegistrationManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.PendingAccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.internal.push.LockedException;

@ProtocolType("verify")
@Doc("verify an account's phone number with a code after registering, completing the account creation process")
public class VerifyRequest implements RequestType<Account> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the e164 phone number being verified") @Required public String account;

  @ExampleValue("\"555555\"") @Doc("the verification code, dash (-) optional") @Required public String code;

  @Override
  public Account run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, AccountHasNoKeysError, AccountAlreadyVerifiedError, AccountLockedError, NoSuchAccountError {

    String server;
    try {
      server = PendingAccountDataTable.getString(account, PendingAccountDataTable.Key.SERVER_UUID);
    } catch (SQLException e) {
      throw new InternalError("error reading from local database", e);
    }
    if (server == null) {
      server = BuildConfig.DEFAULT_SERVER_UUID;
    }
    RegistrationManager rm = null;
    try {
      rm = RegistrationManager.get(account, UUID.fromString(server));
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (SQLException | IOException e) {
      throw new java.lang.InternalError("error getting registration manager", e);
    }
    boolean hasPendingKeys;
    try {
      hasPendingKeys = rm.hasPendingKeys();
    } catch (SQLException e) {
      throw new InternalError("error checking for pending keys", e);
    }

    if (!hasPendingKeys) {
      throw new AccountHasNoKeysError();
    } else if (rm.isRegistered()) {
      throw new AccountAlreadyVerifiedError();
    } else {
      try {
        Manager m = rm.verifyAccount(code);
        return new Account(m.getUUID());
      } catch (LockedException e) {
        logger.warn("Failed to register phone number with PIN lock. See https://gitlab.com/signald/signald/-/issues/47");
        throw new AccountLockedError();
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidKeyException | InvalidInputException | SQLException | IOException e) {
        throw new InternalError("error verifying account", e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
    }
  }
}
