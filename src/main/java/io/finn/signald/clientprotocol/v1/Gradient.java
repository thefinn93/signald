/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;

public class Gradient {
  @JsonProperty("start_color") String startColor;
  @JsonProperty("end_color") String endColor;
  Integer angle;

  public Gradient(SignalServiceTextAttachment.Gradient g) {
    if (g.getStartColor().isPresent()) {
      startColor = Integer.toHexString(g.getStartColor().get());
    }

    if (g.getEndColor().isPresent()) {
      endColor = Integer.toHexString(g.getEndColor().get());
    }

    if (g.getAngle().isPresent()) {
      angle = g.getAngle().get();
    }
  }
}
