/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Deprecated(1641027661)
public class JsonQuotedAttachment {
  public String contentType;
  public String fileName;
  public JsonAttachment thumbnail;

  public JsonQuotedAttachment(){};

  public JsonQuotedAttachment(SignalServiceDataMessage.Quote.QuotedAttachment attachment) {
    contentType = attachment.getContentType();
    fileName = attachment.getFileName();
    // Can't do thumbnails because we can't create a JsonAttachment without the username of the user making the request
    //    if(attachment.getThumbnail() != null) {
    //      thumbnail = new JsonAttachment(attachment.getThumbnail());
    //    }
  }

  @JsonIgnore
  public SignalServiceDataMessage.Quote.QuotedAttachment getAttachment() {
    // FileInputStream thumbnailFile = new FileInputStream(this.thumbnail);
    // SignalServiceAttachmentStream thumbnail = new SignalServiceAttachmentStream(thumbnailFile, this.contentType, thumbnailFile.length,this.Filename, false, null, 0, 0, null,
    // null);
    return new SignalServiceDataMessage.Quote.QuotedAttachment(this.contentType, this.fileName, null);
  }
}
