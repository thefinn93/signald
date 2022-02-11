/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Recipient;
import io.finn.signald.util.AddressUtil;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.util.Base64;

public class ContactStore {
  public final List<ContactInfo> contacts = new ArrayList<>();

  public ContactInfo updateContact(ContactInfo contact) {
    synchronized (contacts) {
      for (ContactInfo c : contacts) {
        if (c.address.matches(contact.address)) {
          c.update(contact);
          return c;
        }
      }
      contacts.add(contact);
    }
    return contact;
  }

  public ContactInfo getContact(Recipient recipient) {
    synchronized (contacts) {
      for (ContactInfo c : contacts) {
        if (c.matches(recipient)) {
          return c;
        }
      }
    }
    return new ContactInfo(recipient);
  }

  public List<ContactInfo> getContacts() {
    synchronized (contacts) { return new ArrayList<>(contacts); }
  }

  public void clear() {
    synchronized (contacts) { contacts.clear(); }
  }

  public static class ContactInfo {
    @JsonProperty public String name;

    @JsonProperty public JsonAddress address;

    @JsonProperty public String color;

    @JsonProperty public String profileKey;

    @JsonProperty public int messageExpirationTime;

    public Integer inboxPosition;

    public ContactInfo() {}

    public ContactInfo(Recipient recipient) { address = new JsonAddress(recipient.getAddress()); }

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

    public boolean matches(Recipient other) { return address.matches(other.getAddress()); }

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

    public void update(SignalContactRecord c) {
      address = new JsonAddress(c.getAddress());

      if (c.getFamilyName().isPresent() || c.getGivenName().isPresent()) {
        if (c.getFamilyName().isPresent() && c.getGivenName().isPresent()) {
          name = c.getFamilyName().get() + " " + c.getGivenName().get();
        } else {
          name = c.getFamilyName().or(c.getGivenName().or(""));
        }
      }

      if (c.getProfileKey().isPresent()) {
        profileKey = Base64.encodeBytes(c.getProfileKey().get());
      }
    }

    public void setVerified(JsonVerifiedState v) {}
  }
}
