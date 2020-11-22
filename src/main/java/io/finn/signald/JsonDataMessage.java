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

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonReaction;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class JsonDataMessage {
  long timestamp;
  List<JsonAttachment> attachments;
  String body;
  JsonGroupInfo group;
  boolean endSession;
  int expiresInSeconds;
  boolean profileKeyUpdate;
  JsonQuote quote;
  List<SharedContact> contacts;
  List<JsonPreview> previews;
  JsonSticker sticker;
  boolean viewOnce;
  JsonReaction reaction;
  SignalServiceDataMessage.RemoteDelete remoteDelete;

  JsonDataMessage(SignalServiceDataMessage dataMessage, String username) throws IOException, NoSuchAccountException {
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
      group = new JsonGroupInfo(dataMessage.getGroupContext().get(), username);
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
      previews = new ArrayList();
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
  }
}
