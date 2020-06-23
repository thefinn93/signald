/*
 * Copyright (C) 2020 Finn Herzfeld
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

import io.finn.signald.storage.JsonAddress;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.util.ArrayList;
import java.util.List;

public class JsonBlockedListMessage {
    List<JsonAddress> addresses;
    List<String> groupIds;
    public JsonBlockedListMessage(BlockedListMessage blocklist) {
        if(!blocklist.getAddresses().isEmpty()) {
            addresses = new ArrayList();
            for(SignalServiceAddress a : blocklist.getAddresses()) {
                addresses.add(new JsonAddress(a));
            }
        }

        if(!blocklist.getGroupIds().isEmpty()) {
            groupIds = new ArrayList<>();
            for(byte[] groupId : blocklist.getGroupIds()) {
                groupIds.add(Base64.encodeBytes(groupId));
            }
        }

    }
}
