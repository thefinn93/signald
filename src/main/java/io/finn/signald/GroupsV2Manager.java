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

package io.finn.signald;

import io.finn.signald.storage.GroupsV2Storage;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class GroupsV2Manager {
    private final GroupsV2Api groupsV2Api;
    private final GroupsV2Storage cache;

    public GroupsV2Manager(GroupsV2Api groupsV2Api, GroupsV2Storage cache) {
        this.groupsV2Api = groupsV2Api;
        this.cache = cache;
    }

    public void RefreshGroupCredentials() throws IOException {
        int today = (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
        HashMap<Integer, AuthCredentialResponse> response = groupsV2Api.getCredentials(today);
    }
}
