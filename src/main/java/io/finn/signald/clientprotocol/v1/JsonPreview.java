/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.ACI;

@Doc("metadata about one of the links in a message")
public class JsonPreview {
  public String url;
  public String title;
  public String description;
  public long date;
  @Doc("an optional image file attached to the preview") public JsonAttachment attachment;

  public JsonPreview() {}

  public JsonPreview(SignalServiceDataMessage.Preview preview, ACI aci) throws InternalError, NoSuchAccountError, ServerNotFoundError, InvalidProxyError {
    url = preview.getUrl();
    title = preview.getTitle();
    description = preview.getDescription();
    date = preview.getDate();
    if (preview.getImage().isPresent()) {
      attachment = new JsonAttachment(preview.getImage().get(), aci);
    }
  }

  public SignalServiceDataMessage.Preview asSignalPreview() throws InvalidAttachmentError {
    SignalServiceAttachment signalServiceAttachment = null;
    if (attachment != null) {
      try {
        signalServiceAttachment = attachment.asStream();
      } catch (IOException e) {
        throw new InvalidAttachmentError(attachment.filename, e);
      }
    }
    return new SignalServiceDataMessage.Preview(url, title, description, date, Optional.fromNullable(signalServiceAttachment));
  }
}