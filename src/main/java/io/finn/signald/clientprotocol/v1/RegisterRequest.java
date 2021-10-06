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
import io.finn.signald.RegistrationManager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.CaptchaRequiredError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;

@ProtocolType("register")
@Doc("begin the account registration process by requesting a phone number verification code. when the code is received, submit it with a verify request")
public class RegisterRequest implements RequestType<Account> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the e164 phone number to register with") @Required public String account;

  @Doc("set to true to request a voice call instead of an SMS for verification") public boolean voice = false;

  @Doc("See https://signald.org/articles/captcha/") public String captcha;

  @Doc("The identifier of the server to use. Leave blank for default (usually Signal production servers but configurable at build time)")
  public String server = BuildConfig.DEFAULT_SERVER_UUID;

  @Override
  public Account run(Request request) throws CaptchaRequiredError, ServerNotFoundError, InvalidProxyError {
    RegistrationManager m;
    try {
      m = RegistrationManager.get(account, UUID.fromString(server));
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (SQLException | IOException e) {
      throw new InternalError("error getting registration manager", e);
    }

    try {
      m.register(voice, Optional.fromNullable(captcha), UUID.fromString(server));
    } catch (CaptchaRequiredException e) {
      throw new CaptchaRequiredError();
    } catch (InvalidInputException | IOException | SQLException e) {
      throw new InternalError("error registering with server", e);
    }
    return new Account(m);
  }
}
