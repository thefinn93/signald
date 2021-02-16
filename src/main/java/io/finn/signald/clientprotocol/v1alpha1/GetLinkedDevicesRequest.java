/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald.clientprotocol.v1alpha1;

import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@SignaldClientRequest(type = "get_linked_devices", ResponseClass = GetLinkedDevicesRequest.LinkedDevices.class)
@Doc("list all linked devices on a Signal account")
@Deprecated
public class GetLinkedDevicesRequest implements RequestType {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException, SQLException {
    SignalServiceAccountManager accountManager = Manager.get(account).getAccountManager();
    request.reply(new LinkedDevices(accountManager.getDevices()));
  }

  public static class LinkedDevices {
    public List<DeviceInfo> devices;
    LinkedDevices(List<DeviceInfo> devices) { this.devices = devices; }
  }
}
