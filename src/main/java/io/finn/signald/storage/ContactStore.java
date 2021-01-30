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

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.util.AddressUtil;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.util.ArrayList;
import java.util.List;

public class ContactStore {
  public List<ContactInfo> contacts = new ArrayList<>();

  public void updateContact(ContactInfo contact) {
    for (ContactInfo c : contacts) {
      if (c.address.matches(contact.address)) {
        c.update(contact);
        return;
      }
    }
    contacts.add(contact);
  }

  public ContactInfo getContact(String identifier) { return getContact(AddressUtil.fromIdentifier(identifier)); }

  public ContactInfo getContact(SignalServiceAddress address) {
    for (ContactInfo c : contacts) {
      if (c.matches(address)) {
        return c;
      }
    }
    return new ContactInfo(address);
  }

  public List<ContactInfo> getContacts() { return contacts; }

  public void clear() { contacts.clear(); }

  public static class ContactInfo {
    @JsonProperty public String name;

    @JsonProperty public JsonAddress address;

    @JsonProperty public String color;

    @JsonProperty public String profileKey;

    @JsonProperty public int messageExpirationTime;

    public Integer inboxPosition;

    public ContactInfo() {}

    public ContactInfo(SignalServiceAddress a) { address = new JsonAddress(a); }

    public void setNumber(@JsonProperty String number) {
      if (address == null) {
        address = new JsonAddress(number);
      } else {
        address.number = number;
      }
    }

    public void setIdentifier(@JsonProperty String identifier) {
      if (address == null) {
        address = new JsonAddress(AddressUtil.fromIdentifier(identifier));
      } else {
        address.uuid = identifier;
      }
    }

    public void update(ContactInfo other) {
      address = AddressUtil.update(address, other.address);
      messageExpirationTime = other.messageExpirationTime;
      if (other.name != null) {
        name = other.name;
      }
      if (other.color != null) {
        color = other.color;
      }
      if (other.profileKey != null) {
        profileKey = other.profileKey;
      }

      if (other.inboxPosition != null) {
        inboxPosition = other.inboxPosition;
      }
    }

    public boolean matches(SignalServiceAddress other) { return address.matches(other); }

    public void update(DeviceContact c) {
      address = new JsonAddress(c.getAddress());

      if (c.getName().isPresent()) {
        name = c.getName().get();
      }

      if (c.getColor().isPresent()) {
        color = c.getColor().get();
      }

      if (c.getProfileKey().isPresent()) {
        profileKey = Base64.encodeBytes(c.getProfileKey().get().serialize());
      }

      if (c.getExpirationTimer().isPresent()) {
        messageExpirationTime = c.getExpirationTimer().get();
      }

      if (c.getInboxPosition().isPresent()) {
        inboxPosition = c.getInboxPosition().get();
      }
    }

    public void setVerified(JsonVerifiedState v) {}
  }
}
