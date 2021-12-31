/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.storage.SignalProfile;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public class Capabilities {
  public boolean gv2;
  public boolean storage;
  @JsonProperty("gv1-migration") public boolean gv1Migration;
  @JsonProperty("sender_key") public boolean senderKey;
  @JsonProperty("announcement_group") public boolean announcementGroup;
  @JsonProperty("change_number") public boolean changeNumber;

  public Capabilities(SignalServiceProfile.Capabilities c) {
    gv2 = c.isGv2();
    storage = c.isStorage();
    gv1Migration = c.isGv1Migration();
    senderKey = c.isSenderKey();
    announcementGroup = c.isAnnouncementGroup();
    changeNumber = c.isChangeNumber();
  }

  public Capabilities(SignalProfile.Capabilities c) {
    gv2 = c.gv2;
    storage = c.storage;
    gv1Migration = c.gv1Migration;
    senderKey = c.senderKey;
    announcementGroup = c.announcementGroup;
    changeNumber = c.changeNumber;
  }
}
