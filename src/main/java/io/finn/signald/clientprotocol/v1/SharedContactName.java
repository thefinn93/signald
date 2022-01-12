/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class SharedContactName {
  @Doc("the full name that should be displayed") public String display;
  @Doc("given name") public String given;
  @Doc("middle name") public String middle;
  @Doc("family name (surname)") public String family;
  public String prefix;
  public String suffix;

  public SharedContactName() {}

  public SharedContactName(SharedContact.Name n) {
    display = n.getDisplay().orNull();
    given = n.getGiven().orNull();
    middle = n.getMiddle().orNull();
    family = n.getFamily().orNull();
    prefix = n.getPrefix().orNull();
    suffix = n.getSuffix().orNull();
  }
}
