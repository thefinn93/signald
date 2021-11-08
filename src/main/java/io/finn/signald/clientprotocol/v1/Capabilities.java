/*
 * Copyright (C) 2021 Finn Herzfeld
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
