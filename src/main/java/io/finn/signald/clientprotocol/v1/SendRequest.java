/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.ACCOUNT;
import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.SignalDependencies;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IGroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.util.Base64;

@ProtocolType("send")
public class SendRequest implements RequestType<SendResponse> {
  private static final Logger logger = LogManager.getLogger();

  @ExactlyOneOfRequired(ACCOUNT) @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) public String username;
  @ExactlyOneOfRequired(ACCOUNT) @ExampleValue(ExampleValue.LOCAL_UUID) public String account;
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
  public SendResponse run(Request request)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, NoSendPermissionError, InvalidAttachmentError, InternalError, InvalidRequestError, UnknownGroupError,
             RateLimitError, InvalidRecipientError, AttachmentTooLargeError, AuthorizationFailedError, SQLError {
    Manager manager = account == null ? Common.getManager(username) : Common.getManager(account);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();

    Recipient recipient = null;
    if (recipientAddress != null) {
      try {
        recipient = Database.Get(manager.getACI()).RecipientsTable.get(recipientAddress);
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
        } catch (AuthorizationFailedException e) {
          throw new AuthorizationFailedError(e);
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
      List<SignalServicePreview> signalPreviews = new ArrayList<>();
      for (JsonPreview preview : previews) {
        signalPreviews.add(preview.asSignalPreview());
      }
      messageBuilder.withPreviews(signalPreviews);
    }

    if (recipientGroupId != null) {
      // check for announcement-only group
      GroupIdentifier groupIdentifier;
      try {
        groupIdentifier = new GroupIdentifier(Base64.decode(recipientGroupId));
      } catch (InvalidInputException | IOException e) {
        throw new InvalidRequestError(e.getMessage());
      }

      Account account = manager.getAccount();

      Optional<IGroupsTable.IGroup> groupOptional;
      try {
        groupOptional = Database.Get(account.getACI()).GroupsTable.get(groupIdentifier);
      } catch (SQLException | InvalidInputException | InvalidProtocolBufferException e) {
        throw new InternalError("unexpected error looking up group to send to", e);
      }

      if (groupOptional.isPresent()) {
        var group = groupOptional.get();
        Recipient self;
        try {
          self = account.getSelf();
        } catch (SQLException | IOException e) {
          throw new InternalError("error verifying own capabilities before sending", e);
        }
        if (group.getDecryptedGroup().getIsAnnouncementGroup() == EnabledState.ENABLED && !group.isAdmin(self)) {
          logger.warn("refusing to send to an announcement only group that we're not an admin in.");
          throw new NoSendPermissionError();
        }
      }
    }

    List<SendMessageResult> results = Common.send(manager, messageBuilder, recipient, recipientGroupId, members);
    return new SendResponse(results, timestamp);
  }
}
