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
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;

@Doc("A local account in signald")
public class Account {
  @Doc(
      "The Signal device ID. Official Signal mobile clients (iPhone and Android) have device ID = 1, while linked devices such as Signal Desktop or Signal iPad have higher device IDs.")
  @JsonProperty("device_id")
  public int deviceID;
  @Doc("The primary identifier on the account, included with all requests to signald for this account. Previously called 'username'")
  @JsonProperty("account_id")
  public String accountID;
  @Doc("The address of this account") public JsonAddress address;

  public Account(Manager m) {
    accountID = m.getUsername();
    deviceID = m.getDeviceId();
    address = new JsonAddress(m.getOwnAddress());
  }
}
