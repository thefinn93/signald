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

package io.finn.signald.util;

import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class GroupsUtil {
    public static GroupsV2Operations GetGroupsV2Operations(SignalServiceConfiguration serviceConfiguration) {
        GroupsV2Operations groupsV2Operations;
        try {
            groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration));
        } catch (Throwable ignored) {
            groupsV2Operations = null;
        }
        return groupsV2Operations;
    }
}
