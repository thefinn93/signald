/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class SharedContactPhone {
  @Doc("the type of phone (options: HOME, WORK, MOBILE, CUSTOM)") public String type;
  @Doc("the phone number") public String value;
  @Doc("the type label when type is CUSTOM") public String label;

  public SharedContactPhone() {}

  public SharedContactPhone(SharedContact.Phone p) {
    type = p.getType().name();
    value = p.getValue();
    label = p.getLabel().orElse(null);
  }
}
