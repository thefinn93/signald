/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class SharedContactAddress {
  @Doc("the type of address (options: HOME, WORK, CUSTOM)") public String type;
  public String label;
  public String street;
  public String pobox;
  public String neighborhood;
  public String city;
  public String region;
  public String postcode;
  public String country;

  public SharedContactAddress() {}

  public SharedContactAddress(SharedContact.PostalAddress a) {
    type = a.getType().name();
    label = a.getLabel().orElse(null);
    street = a.getStreet().orElse(null);
    pobox = a.getPobox().orElse(null);
    neighborhood = a.getNeighborhood().orElse(null);
    city = a.getCity().orElse(null);
    region = a.getRegion().orElse(null);
    postcode = a.getPostcode().orElse(null);
    country = a.getCountry().orElse(null);
  }
}
