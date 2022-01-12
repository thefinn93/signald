/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import org.whispersystems.signalservice.api.messages.shared.SharedContact;

public class SharedContactAvatar {
  public JsonAttachment attachment;
  public boolean is_profile;

  public SharedContactAvatar() {}

  public SharedContactAvatar(SharedContact.Avatar a) {
    attachment = new JsonAttachment(a.getAttachment());
    is_profile = a.isProfile();
  }
}
