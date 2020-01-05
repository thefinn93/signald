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

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class JsonDataMessage {
    long timestamp;
    String message;
    int expiresInSeconds;
    List<JsonAttachment> attachments;
    JsonGroupInfo groupInfo;
    SignalServiceDataMessage.Quote quote;
    List<JsonPreview> previews;
    JsonSticker sticker;

    JsonDataMessage(SignalServiceDataMessage dataMessage, String username) throws IOException, NoSuchAccountException {
        this.timestamp = dataMessage.getTimestamp();
        if (dataMessage.getGroupInfo().isPresent()) {
            this.groupInfo = new JsonGroupInfo(dataMessage.getGroupInfo().get(), username);
        }
        if (dataMessage.getBody().isPresent()) {
            this.message = dataMessage.getBody().get();
        }
        this.expiresInSeconds = dataMessage.getExpiresInSeconds();
        if (dataMessage.getAttachments().isPresent()) {
            this.attachments = new ArrayList<>(dataMessage.getAttachments().get().size());
            for (SignalServiceAttachment attachment : dataMessage.getAttachments().get()) {
                this.attachments.add(new JsonAttachment(attachment, username));
            }
        } else {
            this.attachments = new ArrayList<>();
        }

        if(dataMessage.getQuote().isPresent()) {
          this.quote = dataMessage.getQuote().get();
        }

        if(dataMessage.getPreviews().isPresent()) {
          previews = new ArrayList<JsonPreview>();
          for(SignalServiceDataMessage.Preview p : dataMessage.getPreviews().get()) {
            previews.add(new JsonPreview(p, username));
          }
        }

        if(dataMessage.getSticker().isPresent()) {
          this.sticker = new JsonSticker(dataMessage.getSticker().get(), username);
        }

    }
}
