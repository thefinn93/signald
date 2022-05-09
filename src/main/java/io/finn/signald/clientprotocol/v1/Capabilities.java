/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.db.IProfileCapabilitiesTable;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public class Capabilities {
  @Doc("this capability is deprecated and will always be true") public boolean gv2 = true;
  public boolean storage;
  @JsonProperty("gv1-migration") public boolean gv1Migration;
  @JsonProperty("sender_key") public boolean senderKey;
  @JsonProperty("announcement_group") public boolean announcementGroup;
  @JsonProperty("change_number") public boolean changeNumber;
  public boolean stories;

  public Capabilities(SignalServiceProfile.Capabilities c) { this(new IProfileCapabilitiesTable.Capabilities(c)); }

  public Capabilities(IProfileCapabilitiesTable.Capabilities c) {
    storage = c.isStorage();
    gv1Migration = c.isGv1Migration();
    senderKey = c.isSenderKey();
    announcementGroup = c.isAnnouncementGroup();
    changeNumber = c.isChangeNumber();
    stories = c.isStories();
  }
}
