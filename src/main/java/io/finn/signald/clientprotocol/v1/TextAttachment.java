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
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;
import org.whispersystems.signalservice.api.push.ACI;

public class TextAttachment {
  public String text;
  public String style;
  @JsonProperty("text_foreground_color") public String textForegroundColor;
  @JsonProperty("text_background_color") public String textBackgroundColor;
  public JsonPreview preview;
  @JsonProperty("background_gradient") public Gradient backgroundGradient;
  @JsonProperty("background_color") public String backgroundColor;

  public TextAttachment(SignalServiceTextAttachment t, ACI aci)
      throws NoSuchAccountError, ServerNotFoundError, AuthorizationFailedError, InternalError, InvalidProxyError, NetworkError {
    if (t.getText().isPresent()) {
      text = t.getText().get();
    }

    if (t.getStyle().isPresent()) {
      style = t.getStyle().get().name();
    }

    if (t.getTextForegroundColor().isPresent()) {
      textForegroundColor = Integer.toHexString(t.getTextForegroundColor().get());
    }

    if (t.getTextBackgroundColor().isPresent()) {
      textBackgroundColor = Integer.toHexString(t.getTextBackgroundColor().get());
    }

    if (t.getPreview().isPresent()) {
      preview = new JsonPreview(t.getPreview().get(), aci);
    }

    if (t.getBackgroundGradient().isPresent()) {
      backgroundGradient = new Gradient(t.getBackgroundGradient().get());
    }

    if (t.getBackgroundColor().isPresent()) {
      backgroundColor = Integer.toHexString(t.getBackgroundColor().get());
    }
  }
}
