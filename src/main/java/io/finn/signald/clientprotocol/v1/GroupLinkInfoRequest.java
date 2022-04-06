/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.util.Base64UrlSafe;

@ProtocolType("group_link_info")
@Doc("Get information about a group from a signal.group link")
public class GroupLinkInfoRequest implements RequestType<JsonGroupJoinInfo> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_JOIN_URI) @Doc("the signald.group link") @Required public String uri;

  @Override
  public JsonGroupJoinInfo run(Request request)
      throws GroupLinkNotActiveError, InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRequestError, GroupVerificationError, SQLError {
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
