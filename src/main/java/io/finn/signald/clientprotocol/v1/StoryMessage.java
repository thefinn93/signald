/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.push.ACI;

public class StoryMessage {
  public JsonGroupV2Info group;
  public JsonAttachment file;
  public TextAttachment text;
  @JsonProperty("allow_replies") public Boolean allowReplies;

  public StoryMessage(SignalServiceStoryMessage m, ACI aci) throws NoSuchAccountError, ServerNotFoundError, AuthorizationFailedError, InternalError, InvalidProxyError {
    if (m.getGroupContext().isPresent()) {
      group = new JsonGroupV2Info(m.getGroupContext().get(), aci);
    }

    if (m.getFileAttachment().isPresent()) {
      SignalServiceAttachment attachment = m.getFileAttachment().get();
      file = new JsonAttachment(attachment);
    }

    if (m.getTextAttachment().isPresent()) {
      text = new TextAttachment(m.getTextAttachment().get(), aci);
    }

    if (m.getAllowsReplies().isPresent()) {
      allowReplies = m.getAllowsReplies().get();
    }
  }
}
