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

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@ProtocolType("remote_delete")
@Doc("delete a message previously sent")
public class RemoteDeleteRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the account to use") @Required public String account;

  @ExactlyOneOfRequired(RECIPIENT)
  @Doc("the address to send the delete message to. should match address the message to be deleted was sent to. required if group is not set.")
  public JsonAddress address;

  @ExampleValue(ExampleValue.GROUP_ID)
  @Doc("the group to send the delete message to. should match group the message to be deleted was sent to. required if address is not set.")
  @ExactlyOneOfRequired(RECIPIENT)
  public String group;

  @Required public long timestamp;

  @Override
  public SendResponse run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRecipientError, NoSendPermissionError, UnknownGroupError, RateLimitError {
    Manager m = Common.getManager(account);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withRemoteDelete(new SignalServiceDataMessage.RemoteDelete(timestamp));
    Recipient recipient = null;
    if (address != null) {
      recipient = Common.getRecipient(m.getRecipientsTable(), address);
    }
    List<SendMessageResult> results = Common.send(m, messageBuilder, recipient, group);
    return new SendResponse(results, timestamp);
  }
}
