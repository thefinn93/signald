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
import io.finn.signald.clientprotocol.v1.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RequestUtil {
  public static final List<Class<? extends RequestType>> requestTypes =
      Arrays.asList(SendRequest.class,                                                       // v1        send
                    ReactRequest.class,                                                      // v1        react
                    VersionRequest.class,                                                    // v1        version
                    io.finn.signald.clientprotocol.v1alpha1.ProtocolRequest.class,           // v1alpha1  protocol
                    io.finn.signald.clientprotocol.v1alpha1.GetLinkedDevicesRequest.class,   // v1alpha1  get_linked_devices
                    io.finn.signald.clientprotocol.v1alpha1.RemoveLinkedDeviceRequest.class, // v1alpha1  remove_linked_device
                    io.finn.signald.clientprotocol.v1alpha1.JoinGroupRequest.class,          // v1alpha1  join_group
                    io.finn.signald.clientprotocol.v1alpha1.UpdateGroupRequest.class,        // v1alpha1  update_group
                    io.finn.signald.clientprotocol.v1alpha1.AcceptInvitationRequest.class,   // v1alpha1  accept_invitation
                    io.finn.signald.clientprotocol.v1alpha1.ApproveMembershipRequest.class,  // v1alpha1  approve_membership
                    io.finn.signald.clientprotocol.v1alpha1.GetGroupRequest.class,           // v1alpha1  get_group
                    io.finn.signald.clientprotocol.v1alpha2.UpdateGroupRequest.class,        // v1alpha2  update_group
                    AcceptInvitationRequest.class,                                           // v1        accept_invitation
                    ApproveMembershipRequest.class,                                          // v1        approve_membership
                    GetGroupRequest.class,                                                   // v1        get_group
                    GetLinkedDevicesRequest.class,                                           // v1        get_linked_devices
                    JoinGroupRequest.class,                                                  // v1        join_group
                    ProtocolRequest.class,                                                   // v1        protocol
                    RemoveLinkedDeviceRequest.class,                                         // v1        remove_linked_device
                    UpdateGroupRequest.class,                                                // v1        update_group
                    SetProfile.class,                                                        // v1        set_profile
                    ResolveAddressRequest.class,                                             // v1        resolve_address
                    MarkReadRequest.class,                                                   // v1        mark_read
                    GetProfileRequest.class,                                                 // v1        get_profile
                    ListGroupsRequest.class,                                                 // v1        list_groups
                    ListContactsRequest.class,                                               // v1        list_contacts
                    CreateGroupRequest.class,                                                // v1        create_group
                    LeaveGroupRequest.class,                                                  // v1        leave_group
                    GenerateLinkingURIRequest.class,                                         // v1        generate_linking_uri
                    FinishLinkRequest.class                                                  // v1        finish_link
      );

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
