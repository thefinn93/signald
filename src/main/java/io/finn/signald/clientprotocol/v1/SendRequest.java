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

import io.finn.signald.JsonAttachment;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

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

  @Override
  public SendResponse run(Request request)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, NoSendPermissionError, InvalidAttachmentError, InternalError, UnknownGroupError, InvalidRecipientError {
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
      List<SignalServiceAttachment> signalServiceAttachments = new ArrayList<>(attachments.size());
      for (JsonAttachment attachment : attachments) {
        try {
          File attachmentFile = new File(attachment.filename);
          InputStream attachmentStream = new FileInputStream(attachmentFile);
          final long attachmentSize = attachmentFile.length();
          if (attachment.contentType == null) {
            attachment.contentType = Files.probeContentType(attachmentFile.toPath());
            if (attachment.contentType == null) {
              attachment.contentType = "application/octet-stream";
            }
          }
          String customFilename = attachmentFile.getName();
          if (attachment.customFilename != null) {
            customFilename = attachment.customFilename;
          }
          signalServiceAttachments.add(new SignalServiceAttachmentStream(
              attachmentStream, attachment.contentType, attachmentSize, Optional.of(customFilename), attachment.voiceNote, false, false, attachment.getPreview(), attachment.width,
              attachment.height, System.currentTimeMillis(), Optional.fromNullable(attachment.caption), Optional.fromNullable(attachment.blurhash), null, null, Optional.absent()));
        } catch (IOException e) {
          throw new InvalidAttachmentError(attachment.filename, e);
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

    List<SendMessageResult> results;

    results = Common.send(manager, messageBuilder, recipient, recipientGroupId);
    return new SendResponse(results, timestamp);
  }
}
