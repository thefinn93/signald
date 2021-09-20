/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonPreview {
  public String url;
  public String title;
  public JsonAttachment attachment;

  public JsonPreview(SignalServiceDataMessage.Preview preview, UUID accountUUID) throws InternalError, NoSuchAccountError, ServerNotFoundError, InvalidProxyError {
    url = preview.getUrl();
    title = preview.getTitle();
    if (preview.getImage().isPresent()) {
      attachment = new JsonAttachment(preview.getImage().get(), accountUUID);
    }
  }
}