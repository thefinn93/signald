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

import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.ReactRequest;
import io.finn.signald.clientprotocol.v1.SendRequest;
import io.finn.signald.clientprotocol.v1.VersionRequest;
import io.finn.signald.clientprotocol.v1alpha1.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RequestUtil {
  public static final List<Class<? extends RequestType>> requestTypes =
      Arrays.asList(SendRequest.class, ReactRequest.class, VersionRequest.class, ProtocolRequest.class, GetLinkedDevicesRequest.class, RemoveLinkedDeviceRequest.class,
                    JoinGroupRequest.class, io.finn.signald.clientprotocol.v1alpha1.UpdateGroupRequest.class, AcceptInvitationRequest.class, ApproveMembershipRequest.class,
                    GetGroupRequest.class, io.finn.signald.clientprotocol.v1alpha2.UpdateGroupRequest.class);

  public static String getVersion(Class t) {
    if (t == null || t.isPrimitive() || t == UUID.class || t == Map.class) {
      return null;
    }
    String pkg = t.getName().replace("." + t.getSimpleName(), "");
    if (pkg.equals("java.lang")) {
      return null;
    }

    if (!pkg.startsWith("io.finn.signald.clientprotocol")) {
      return "v0";
    }

    pkg = pkg.replace("io.finn.signald.clientprotocol.", "");
    if (!pkg.contains(".")) {
      return pkg;
    }
    return pkg.substring(0, pkg.indexOf("."));
  }
}
