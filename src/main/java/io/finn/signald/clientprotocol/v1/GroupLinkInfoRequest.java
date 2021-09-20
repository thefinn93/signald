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
import java.io.IOException;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;

@ProtocolType("group_link_info")
@Doc("Get information about a group from a signal.group link")
public class GroupLinkInfoRequest implements RequestType<JsonGroupJoinInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_JOIN_URI) @Doc("the signald.group link") @Required public String uri;

  @Override
  public JsonGroupJoinInfo run(Request request)
      throws GroupLinkNotActiveError, InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRequestError, GroupVerificationError {
    try {
      return Common.getManager(account).getGroupsV2Manager().getGroupJoinInfo(uri);
    } catch (GroupLinkNotActiveException e) {
      throw new GroupLinkNotActiveError();
    } catch (InvalidInputException e) {
      throw new InvalidRequestError("invalid group URI");
    } catch (IOException e) {
      throw new InternalError("error getting group join info", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    }
  }
}
