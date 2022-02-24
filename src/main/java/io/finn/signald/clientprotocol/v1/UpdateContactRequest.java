/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
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
  public Profile run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError {
    ContactStore.ContactInfo c = new ContactStore.ContactInfo();
    c.address = address;
    c.name = name;
    c.color = color;
    c.inboxPosition = inboxPosition;
    ContactStore.ContactInfo contactInfo;

    Manager m = Common.getManager(account);
    try {
      contactInfo = m.updateContact(c);
    } catch (IOException | SQLException e) {
      throw new InternalError("error updating contact", e);
    }
    Common.saveAccount(m.getAccountData());
    return new Profile(contactInfo);
  }
}
