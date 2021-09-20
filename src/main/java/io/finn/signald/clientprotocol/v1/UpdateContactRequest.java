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
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.storage.ContactStore;
import java.io.IOException;
import java.sql.SQLException;

@ProtocolType("update_contact")
@Doc("update information about a local contact")
public class UpdateContactRequest implements RequestType<Profile> {
  @Required public String account;
  @Required public JsonAddress address;
  public String name;
  public String color;
  @JsonProperty("inbox_position") public Integer inboxPosition;

  @Override
  public Profile run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    ContactStore.ContactInfo c = new ContactStore.ContactInfo();
    c.address = address;
    c.name = name;
    c.color = color;
    c.inboxPosition = inboxPosition;
    ContactStore.ContactInfo contactInfo;
    try {
      contactInfo = Common.getManager(account).updateContact(c);
    } catch (IOException | SQLException e) {
      throw new InternalError("error updating contact", e);
    }
    return new Profile(contactInfo);
  }
}
