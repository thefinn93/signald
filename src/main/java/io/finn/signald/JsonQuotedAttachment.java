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


class JsonQuotedAttachment {
  public String contentType;
  public String fileName;
  public String thumbnail;

  public SignalServiceDataMessage.Quote.QuotedAttachment getAttachment() {
    // FileInputStream thumbnailFile = new FileInputStream(this.thumbnail);
    // SignalServiceAttachmentStream thumbnail = new SignalServiceAttachmentStream(thumbnailFile, this.contentType, thumbnailFile.length,this.Filename, false, null, 0, 0, null, null);
    return new SignalServiceDataMessage.Quote.QuotedAttachment(this.contentType, this.fileName, null);
  }
}
