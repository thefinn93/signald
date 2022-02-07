/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Manager;
import io.finn.signald.SignalDependencies;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

@ProtocolType("send")
public class SendRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Required public String username;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress recipientAddress;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String recipientGroupId;
  @ExampleValue(ExampleValue.MESSAGE_BODY) @AtLeastOneOfRequired({"attachments"}) public String messageBody;
  @AtLeastOneOfRequired({"messageBody"}) public List<JsonAttachment> attachments;
  public JsonQuote quote;
  public Long timestamp;
  public List<JsonMention> mentions;
  public List<JsonPreview> previews;
  @Doc("Optionally set to a sub-set of group members. Ignored if recipientGroupId isn't specified") public List<JsonAddress> members;

  @Override
  public SendResponse run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, NoSendPermissionError, InvalidAttachmentError, InternalError,
                                                  InvalidRequestError, UnknownGroupError, RateLimitError, InvalidRecipientError, AttachmentTooLargeError {
    Manager manager = Common.getManager(username);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();

    Recipient recipient = null;
    if (recipientAddress != null) {
      try {
        recipient = manager.getRecipientsTable().get(recipientAddress);
      } catch (IOException | SQLException e) {
        throw new InternalError("error looking up recipient", e);
      }
    }

    if (messageBody != null) {
      messageBuilder = messageBuilder.withBody(messageBody);
    }

    if (attachments != null) {
      SignalServiceMessageSender sender;
      try {
        sender = SignalDependencies.get(manager.getACI()).getMessageSender();
      } catch (SQLException | IOException e) {
        throw new InternalError("unexpected error getting message sender to upload attachments", e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      }

      List<SignalServiceAttachment> signalServiceAttachments = new ArrayList<>(attachments.size());
      for (JsonAttachment attachment : attachments) {
        try {
          signalServiceAttachments.add(sender.uploadAttachment(attachment.asStream()));
        } catch (NonSuccessfulResponseCodeException e) {
          if (e.getCode() == 400) {
            throw new AttachmentTooLargeError(attachment.filename);
          } else {
            throw new InternalError("error uploading attachment", e);
          }
        } catch (IOException e) {
          throw new InternalError("error uploading attachment", e);
        }
      }
      messageBuilder.withAttachments(signalServiceAttachments);
    }

    if (quote != null) {
      messageBuilder.withQuote(quote.getQuote());
    }

    if (timestamp == null) {
      timestamp = System.currentTimeMillis();
    }
    messageBuilder.withTimestamp(timestamp);

    if (mentions != null && mentions.size() > 0) {
      messageBuilder.withMentions(mentions.stream().map(JsonMention::asMention).collect(Collectors.toList()));
    }

    if (previews != null) {
      List<SignalServiceDataMessage.Preview> signalPreviews = new ArrayList<>();
      for (JsonPreview preview : previews) {
        signalPreviews.add(preview.asSignalPreview());
      }
      messageBuilder.withPreviews(signalPreviews);
    }

    List<SendMessageResult> results;

    results = Common.send(manager, messageBuilder, recipient, recipientGroupId, members);
    return new SendResponse(results, timestamp);
  }
}
