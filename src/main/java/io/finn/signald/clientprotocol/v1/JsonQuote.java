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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.JsonQuotedAttachment;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.ArrayList;
import java.util.List;

@Doc("A quote is a reply to a previous message. ID is the sent time of the message being replied to")
public class JsonQuote {
  @ExampleValue(ExampleValue.MESSAGE_ID) @Doc("the client timestamp of the message being quoted") public long id;
  @Doc("the author of the message being quoted") public JsonAddress author;
  @ExampleValue(ExampleValue.QUOTED_MESSAGE_BODY) @Doc("the body of the message being quoted") public String text;
  @Doc("list of files attached to the quoted message") public List<JsonQuotedAttachment> attachments;
  @Doc("list of mentions in the quoted message") public List<JsonMention> mentions = new ArrayList<>();

  public JsonQuote() {}

  public JsonQuote(SignalServiceDataMessage.Quote quote) {
    id = quote.getId();
    author = new JsonAddress(quote.getAuthor());
    text = quote.getText();
    if (quote.getAttachments() != null && !quote.getAttachments().isEmpty()) {
      attachments = new ArrayList<>();
      for (SignalServiceDataMessage.Quote.QuotedAttachment a : quote.getAttachments()) {
        attachments.add(new JsonQuotedAttachment(a));
      }
    }

    if (quote.getMentions() != null) {
      for (SignalServiceDataMessage.Mention mention : quote.getMentions()) {
        mentions.add(new JsonMention(mention));
      }
    }
  }

  @JsonIgnore
  public SignalServiceDataMessage.Quote getQuote() {
    ArrayList<SignalServiceDataMessage.Quote.QuotedAttachment> quotedAttachments = new ArrayList<>();

    if (attachments != null) {
      for (JsonQuotedAttachment attachment : this.attachments) {
        quotedAttachments.add(attachment.getAttachment());
      }
    }

    List<SignalServiceDataMessage.Mention> signalMentions = new ArrayList<>();
    for (JsonMention mention : mentions) {
      signalMentions.add(mention.asMention());
    }
    return new SignalServiceDataMessage.Quote(this.id, this.author.getSignalServiceAddress(), this.text, quotedAttachments, signalMentions);
  }
}
