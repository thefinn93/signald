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
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.AccountAlreadyVerified;
import io.finn.signald.exceptions.AccountHasNoKeys;
import io.finn.signald.exceptions.JsonifyableException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.internal.push.LockedException;

import java.io.IOException;
import java.sql.SQLException;

@SignaldClientRequest(type = "verify")
@Doc("verify an account's phone number with a code after registering, completing the account creation process")
public class VerifyRequest implements RequestType<Account> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the e164 phone number being verified") @Required public String account;

  @ExampleValue("\"555555\"") @Doc("the verification code, dash (-) optional") @Required public String code;

  @Override
  public Account run(Request request) throws SQLException, IOException, NoSuchAccountException, JsonifyableException, InvalidInputException {
    Manager m = Manager.getPending(account);
    if (!m.hasPendingKeys()) {
      throw new AccountHasNoKeys();
    } else if (m.isRegistered()) {
      throw new AccountAlreadyVerified();
    } else {
      try {
        m.verifyAccount(code);
      } catch (LockedException e) {
        logger.warn("Failed to register phone number with PIN lock. See https://gitlab.com/signald/signald/-/issues/47");
        throw new JsonifyableException(e);
      }
    }
    return new Account(m);
  }
}
