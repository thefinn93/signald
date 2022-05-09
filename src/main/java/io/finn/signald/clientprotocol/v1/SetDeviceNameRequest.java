/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.Empty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

@ProtocolType("set_device_name")
@Doc("set this device's name. This will show up on the mobile device on the same account under settings -> linked devices")
public class SetDeviceNameRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to set the device name of") @Required public String account;

  @JsonProperty("device_name") @Doc("The device name") public String deviceName;
  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, AuthorizationFailedError, SQLError, InvalidRequestError {
    Account a = Common.getAccount(account);

    try {
      a.setDeviceName(deviceName);
    } catch (SQLException e) {
      throw new InternalError("error storing new device name locally", e);
    }

    try {
      a.refresh();
    } catch (IOException | SQLException e) {
      throw new InternalError("error saving new device name", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }
    return new Empty();
  }
}
