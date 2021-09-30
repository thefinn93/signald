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

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;

@ProtocolType("get_group")
@Doc("Query the server for the latest state of a known group. If no account in signald is a member of the group "
     + "(anymore), an error with error_type: 'UnknownGroupError' is returned.")
public class GetGroupRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Doc("the latest known revision, default value (-1) forces fetch from server") public int revision = -1;

  @Override
  public JsonGroupV2Info run(Request request)
      throws NoSuchAccountError, UnknownGroupError, ServerNotFoundError, InvalidProxyError, InternalError, GroupVerificationError, InvalidGroupStateError, InvalidRequestError {
    return Common.getGroup(Common.getAccount(account), groupID).getJsonGroupV2Info();
  }
}
