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

import io.finn.signald.Groups;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.util.Base64UrlSafe;

@ProtocolType("group_link_info")
@Doc("Get information about a group from a signal.group link")
public class GroupLinkInfoRequest implements RequestType<JsonGroupJoinInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_JOIN_URI) @Doc("the signald.group link") @Required public String uri;

  @Override
  public JsonGroupJoinInfo run(Request request)
      throws GroupLinkNotActiveError, InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRequestError, GroupVerificationError {
    URI parsedURI;
    try {
      parsedURI = new URI(uri);
    } catch (URISyntaxException e) {
      throw new InvalidRequestError(e.getMessage());
    }

    String encoding = parsedURI.getFragment();
    if (encoding == null || encoding.length() == 0) {
      throw new InvalidRequestError("unable to get encoding from URI");
    }

    GroupInviteLink groupInviteLink;
    try {
      byte[] groupInviteLinkBytes = Base64UrlSafe.decodePaddingAgnostic(encoding);
      groupInviteLink = GroupInviteLink.parseFrom(groupInviteLinkBytes);
    } catch (IOException e) {
      throw new InvalidRequestError(e.getMessage());
    }

    GroupInviteLink.GroupInviteLinkContentsV1 groupInviteLinkContentsV1 = groupInviteLink.getV1Contents();

    GroupMasterKey groupMasterKey;
    try {
      groupMasterKey = new GroupMasterKey(groupInviteLinkContentsV1.getGroupMasterKey().toByteArray());
    } catch (InvalidInputException e) {
      throw new InternalError("error getting group master key", e);
    }

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    Groups groups = Common.getGroups(Common.getAccount(account));
    DecryptedGroupJoinInfo decryptedGroupJoinInfo;
    try {
      decryptedGroupJoinInfo = groups.getGroupJoinInfo(groupSecretParams, groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray());
    } catch (IOException | InvalidInputException | SQLException | VerificationFailedException e) {
      throw new InternalError("error getting group join info", e);
    } catch (GroupLinkNotActiveException e) {
      throw new GroupLinkNotActiveError();
    }

    return new JsonGroupJoinInfo(decryptedGroupJoinInfo, groupMasterKey);
  }
}
