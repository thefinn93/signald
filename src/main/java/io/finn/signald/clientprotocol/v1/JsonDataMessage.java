/*
 * Copyright (C) 2020 Finn Herzfeld
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

import io.finn.signald.JsonAttachment;
import io.finn.signald.JsonPreview;
import io.finn.signald.JsonSticker;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JsonDataMessage {
  @Doc("the (unix) timestamp that the message was sent at, according to the sender's device. This is used to uniquely identify this message for things like reactions and quotes.")
  public long timestamp;

  @Doc("files attached to the incoming message") public List<JsonAttachment> attachments;

  @Doc("the text body of the incoming message.") public String body;

  @Doc("if the incoming message was sent to a v1 group, information about that group will be here") public JsonGroupInfo group;

  @Doc("is the incoming message was sent to a v2 group, basic identifying information about that group will be here. For full information, use list_groups")
  public JsonGroupV2Info groupV2;

  public boolean endSession;

  @Doc("the expiry timer on the incoming message. Clients should delete records of the message within this number of seconds") public int expiresInSeconds;
  public boolean profileKeyUpdate;

  @Doc("if the incoming message is a quote or reply to another message, this will contain information about that message") public JsonQuote quote;

  @Doc("if the incoming message has a shared contact, the contact's information will be here") public List<SharedContact> contacts;

  @Doc("if the incoming message has a link preview, information about that preview will be here") public List<JsonPreview> previews;

  @Doc("if the incoming message is a sticker, information about the sicker will be here") public JsonSticker sticker;

  @Doc(
      "indicates the message is a view once message. View once messages typically include no body and a single image attachment. Official Signal clients will prevent the user from saving the image, and once the user has viewed the image once they will destroy the image.")
  public boolean viewOnce;

  @Doc("if the message adds or removes a reaction to another message, this will indicate what change is being made") public JsonReaction reaction;

  @Doc("if the inbound message is deleting a previously sent message, indicates which message should be deleted") public SignalServiceDataMessage.RemoteDelete remoteDelete;

  @Doc("list of mentions in the message") public List<JsonMention> mentions;

  public JsonDataMessage(SignalServiceDataMessage dataMessage, String username) throws IOException, NoSuchAccountException {
    timestamp = dataMessage.getTimestamp();

    if (dataMessage.getAttachments().isPresent()) {
      attachments = new ArrayList<>(dataMessage.getAttachments().get().size());
      for (SignalServiceAttachment attachment : dataMessage.getAttachments().get()) {
        attachments.add(new JsonAttachment(attachment, username));
      }
    }

    if (dataMessage.getBody().isPresent()) {
      body = dataMessage.getBody().get();
    }

    if (dataMessage.getGroupContext().isPresent()) {
      SignalServiceGroupContext groupContext = dataMessage.getGroupContext().get();
      if (groupContext.getGroupV1().isPresent()) {
        group = new JsonGroupInfo(groupContext.getGroupV1().get(), username);
      }
      if (groupContext.getGroupV2().isPresent()) {
        groupV2 = new JsonGroupV2Info(groupContext.getGroupV2().get(), null).sanitized();
      }
    }

    endSession = dataMessage.isEndSession();

    if (dataMessage.isExpirationUpdate()) {
      expiresInSeconds = dataMessage.getExpiresInSeconds();
    }

    profileKeyUpdate = dataMessage.isProfileKeyUpdate();

    if (dataMessage.getQuote().isPresent()) {
      quote = new JsonQuote(dataMessage.getQuote().get());
    }

    if (dataMessage.getSharedContacts().isPresent()) {
      contacts = dataMessage.getSharedContacts().get();
    }

    if (dataMessage.getPreviews().isPresent()) {
      previews = new ArrayList<>();
      for (SignalServiceDataMessage.Preview p : dataMessage.getPreviews().get()) {
        previews.add(new JsonPreview(p, username));
      }
    }

    if (dataMessage.getSticker().isPresent()) {
      sticker = new JsonSticker(dataMessage.getSticker().get(), username);
    }

    viewOnce = dataMessage.isViewOnce();

    if (dataMessage.getReaction().isPresent()) {
      reaction = new JsonReaction(dataMessage.getReaction().get());
    }

    if (dataMessage.getRemoteDelete().isPresent()) {
      remoteDelete = dataMessage.getRemoteDelete().get();
    }

    if (dataMessage.getMentions().isPresent()) {
      mentions = new ArrayList<>();
      for (SignalServiceDataMessage.Mention mention : dataMessage.getMentions().get()) {
        mentions.add(new JsonMention(mention));
      }
    }
  }
}
