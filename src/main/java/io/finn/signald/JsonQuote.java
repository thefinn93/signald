/**
 * Copyright (C) 2018 Finn Herzfeld
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

import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;


class JsonQuote {
  public long id;
  public String author;
  public String text;
  public List<JsonQuotedAttachment> attachments = new ArrayList<>();

  public SignalServiceDataMessage.Quote getQuote() {
    ArrayList<SignalServiceDataMessage.Quote.QuotedAttachment> quotedAttachments = new ArrayList<SignalServiceDataMessage.Quote.QuotedAttachment>();
    for(JsonQuotedAttachment attachment : this.attachments) {
      quotedAttachments.add(attachment.getAttachment());
    }
    return new SignalServiceDataMessage.Quote(this.id, new SignalServiceAddress(this.author), this.text, quotedAttachments);
  }
}
