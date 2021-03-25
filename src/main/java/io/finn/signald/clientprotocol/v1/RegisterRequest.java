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
import io.finn.signald.db.PendingAccountDataTable;
import io.finn.signald.exceptions.CaptchaRequired;
import io.finn.signald.util.KeyUtil;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;

import java.io.IOException;
import java.sql.SQLException;

@SignaldClientRequest(type = "register")
@Doc("begin the account registration process by requesting a phone number verification code. when the code is received, submit it with a verify request")
public class RegisterRequest implements RequestType<Account> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the e164 phone number to register with") @Required public String account;

  @Doc("set to true to request a voice call instead of an SMS for verification") public boolean voice = false;

  @Doc("See https://docs.signald.org/articles/captcha/") public String captcha;

  @Override
  public Account run(Request request) throws SQLException, IOException, NoSuchAccountException, InvalidInputException, CaptchaRequired {
    Manager m = Manager.getPending(account);

    IdentityKeyPair identityKey = KeyUtil.generateIdentityKeyPair();
    PendingAccountDataTable.set(account, PendingAccountDataTable.Key.LOCAL_REGISTRATION_ID, identityKey.serialize());

    int registrationId = KeyHelper.generateRegistrationId(false);
    PendingAccountDataTable.set(account, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR, registrationId);

    try {
      m.register(voice, Optional.fromNullable(captcha));
    } catch (CaptchaRequiredException e) {
      throw new CaptchaRequired();
    }
    return new Account(m);
  }
}
