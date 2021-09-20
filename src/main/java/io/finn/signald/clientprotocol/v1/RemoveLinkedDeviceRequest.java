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

import io.finn.signald.Empty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.io.IOException;
import java.util.UUID;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

@Doc("Remove a linked device from the Signal account. Only allowed when the local device id is 1")
@ProtocolType("remove_linked_device")
public class RemoveLinkedDeviceRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue("3") @Doc("the ID of the device to unlink") @Required public long deviceId;

  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    UUID accountUUID = Common.getAccount(account);
    SignalServiceAccountManager accountManager = Common.getDependencies(accountUUID).getAccountManager();
    try {
      accountManager.removeDevice(deviceId);
    } catch (IOException e) {
      throw new InternalError("error removing device", e);
    }
    return new Empty();
  }
}