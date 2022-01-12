/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class SharedContactEmail {
  @Doc("the type of email (options: HOME, WORK, MOBILE, CUSTOM)") public String type;
  @Doc("the email address") public String value;
  @Doc("the type label when type is CUSTOM") public String label;

  public SharedContactEmail() {}

  public SharedContactEmail(SharedContact.Email e) {
    type = e.getType().name();
    value = e.getValue();
    label = e.getLabel().orNull();
  }
}
