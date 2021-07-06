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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import io.finn.signald.db.AccountDataTable;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidKeyException;

@ProtocolType("set_device_name")
@Doc("set this device's name. This will show up on the mobile device on the same account under ")
public class SetDeviceNameRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to set the device name of") @Required public String account;

  @JsonProperty("device_name") @Doc("The device name") public String deviceName;
  @Override
  public Empty run(Request request) throws SQLException, IOException, NoSuchAccount, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Utils.getManager(account);
    AccountDataTable.set(m.getUUID(), AccountDataTable.Key.DEVICE_NAME, deviceName);
    m.refreshAccount();
    return new Empty();
  }
}
