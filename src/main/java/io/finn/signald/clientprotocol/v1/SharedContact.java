/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import java.util.ArrayList;
import java.util.List;

public class SharedContact {
  @Doc("the name of the shared contact") public SharedContactName name;
  @Doc("the email addresses of the shared contact") public List<SharedContactEmail> email;
  @Doc("the phone numbers of the shared contact") public List<SharedContactPhone> phone;
  @Doc("the physical addresses of the shared contact") public List<SharedContactAddress> address;
  @Doc("the profile picture/avatar of the shared contact") public SharedContactAvatar avatar;
  @Doc("the organization (e.g. workplace) of the shared contact") public String organization;

  public SharedContact() {}

  public SharedContact(org.whispersystems.signalservice.api.messages.shared.SharedContact c) {
    name = new SharedContactName(c.getName());
    organization = c.getOrganization().orNull();

    if (c.getEmail().isPresent()) {
      email = new ArrayList<>();
      for (org.whispersystems.signalservice.api.messages.shared.SharedContact.Email e : c.getEmail().get()) {
        email.add(new SharedContactEmail(e));
      }
    }

    if (c.getPhone().isPresent()) {
      phone = new ArrayList<>();
      for (org.whispersystems.signalservice.api.messages.shared.SharedContact.Phone p : c.getPhone().get()) {
        phone.add(new SharedContactPhone(p));
      }
    }

    if (c.getAddress().isPresent()) {
      address = new ArrayList<>();
      for (org.whispersystems.signalservice.api.messages.shared.SharedContact.PostalAddress a : c.getAddress().get()) {
        address.add(new SharedContactAddress(a));
      }
    }

    if (c.getAvatar().isPresent()) {
      avatar = new SharedContactAvatar(c.getAvatar().get());
    }
  }
}
