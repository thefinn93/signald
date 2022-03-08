/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;

@ProtocolType("get_group")
@Doc("Query the server for the latest state of a known group. If the account is not a member of the group, an "
     + "UnknownGroupError is returned.")
public class GetGroupRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Doc("the latest known revision, default value (-1) forces fetch from server") public int revision = -1;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, UnknownGroupError, ServerNotFoundError, InvalidProxyError, InternalError, GroupVerificationError,
                                                     InvalidGroupStateError, InvalidRequestError, AuthorizationFailedError, SQLError {
    return Common.getGroup(Common.getAccount(account), groupID, revision).getJsonGroupV2Info();
  }
}
