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

package io.finn.signald.util;

import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.v1.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RequestUtil {
  public static final List<Class<? extends io.finn.signald.clientprotocol.RequestType<?>>> REQUEST_TYPES = Arrays.asList( // version   request_type
      SendRequest.class,                                                                                                  // v1        send
      ReactRequest.class,                                                                                                 // v1        react
      VersionRequest.class,                                                                                               // v1        version
      AcceptInvitationRequest.class,                                                                                      // v1        accept_invitation
      ApproveMembershipRequest.class,                                                                                     // v1        approve_membership
      GetGroupRequest.class,                                                                                              // v1        get_group
      GetLinkedDevicesRequest.class,                                                                                      // v1        get_linked_devices
      JoinGroupRequest.class,                                                                                             // v1        join_group
      ProtocolRequest.class,                                                                                              // v1        protocol
      RemoveLinkedDeviceRequest.class,                                                                                    // v1        remove_linked_device
      UpdateGroupRequest.class,                                                                                           // v1        update_group
      SetProfile.class,                                                                                                   // v1        set_profile
      ResolveAddressRequest.class,                                                                                        // v1        resolve_address
      MarkReadRequest.class,                                                                                              // v1        mark_read
      GetProfileRequest.class,                                                                                            // v1        get_profile
      ListGroupsRequest.class,                                                                                            // v1        list_groups
      ListContactsRequest.class,                                                                                          // v1        list_contacts
      CreateGroupRequest.class,                                                                                           // v1        create_group
      LeaveGroupRequest.class,                                                                                            // v1        leave_group
      GenerateLinkingURIRequest.class,                                                                                    // v1        generate_linking_uri
      FinishLinkRequest.class,                                                                                            // v1        finish_link
      AddLinkedDeviceRequest.class,                                                                                       // v1        add_device
      RegisterRequest.class,                                                                                              // v1        register
      VerifyRequest.class,                                                                                                // v1        verify
      GetIdentitiesRequest.class,                                                                                         // v1        get_identities
      TrustRequest.class,                                                                                                 // v1        trust
      DeleteAccountRequest.class,                                                                                         // v1        delete_account
      TypingRequest.class,                                                                                                // v1        typing
      ResetSessionRequest.class,                                                                                          // v1        reset_session
      RequestSyncRequest.class,                                                                                           // v1        request_sync
      ListAccountsRequest.class,                                                                                          // v1        list_accounts
      GroupLinkInfoRequest.class,                                                                                         // v1        group_link_info
      UpdateContactRequest.class,                                                                                         // v1        update_contact
      SetExpirationRequest.class,                                                                                         // v1        set_expiration
      SetDeviceNameRequest.class,                                                                                         // v1        set_device_name
      GetAllIdentities.class,                                                                                             // v1        get_all_identities
      SubscribeRequest.class,                                                                                             // v1        subscribe
      UnsubscribeRequest.class,                                                                                           // v1        unsubscribe
      RemoteDeleteRequest.class,                                                                                          // v1        remote_delete
      AddServerRequest.class,                                                                                             // v1        add_server
      GetServersRequest.class,                                                                                            // v1        get_servers
      RemoveServerRequest.class,                                                                                          // v1        remove_server
      SendPaymentRequest.class,                                                                                           // v1        send_payment
      RemoteConfigRequest.class,                                                                                          // v1        get_remote_config
      RefuseMembershipRequest.class                                                                                       // v1        refuse_membership
  );

  public static String getVersion(Class<?> t) {
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
    pkg = pkg.replace(".exceptions", "");
    if (!pkg.contains(".")) {
      return pkg;
    }
    return pkg.substring(0, pkg.indexOf("."));
  }

  public static String getType(Class<?> t) {
    ProtocolType annotation = t.getAnnotation(ProtocolType.class);
    if (annotation == null) {
      return t.getSimpleName();
    }
    return annotation.value();
  }
}
