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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;


class JsonQuote {
  public long id;
  public JsonAddress author;
  public String text;
  public List<JsonQuotedAttachment> attachments;
  public List<SignalServiceDataMessage.Mention> mentions = new ArrayList<>();

  public JsonQuote() {}

  public JsonQuote(SignalServiceDataMessage.Quote quote) {
    id = quote.getId();
    author = new JsonAddress(quote.getAuthor());
    text = quote.getText();
    if(quote.getAttachments() != null && !quote.getAttachments().isEmpty()) {
      attachments = new ArrayList<>();
      for(SignalServiceDataMessage.Quote.QuotedAttachment a : quote.getAttachments()) {
        attachments.add(new JsonQuotedAttachment(a));
      }
    }
  }

  @JsonIgnore
  public SignalServiceDataMessage.Quote getQuote() {
    ArrayList<SignalServiceDataMessage.Quote.QuotedAttachment> quotedAttachments = new ArrayList<>();

    if(attachments != null) {
      for (JsonQuotedAttachment attachment : this.attachments) {
        quotedAttachments.add(attachment.getAttachment());
      }
    }
    return new SignalServiceDataMessage.Quote(this.id, this.author.getSignalServiceAddress(), this.text, quotedAttachments, mentions);
  }
}
