/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;

public class Gradient {
  @Doc("removed from Signal protocol") @Deprecated(0) @JsonProperty("start_color") String startColor;
  @Doc("removed from Signal protocol") @Deprecated(0) @JsonProperty("end_color") String endColor;
  @JsonProperty List<String> colors;
  @JsonProperty Integer angle;
  @JsonProperty List<Float> positions;

  public Gradient(SignalServiceTextAttachment.Gradient g) {
    colors = g.getColors().stream().map(Integer::toHexString).collect(Collectors.toList());
    positions = g.getPositions();

    if (g.getAngle().isPresent()) {
      angle = g.getAngle().get();
    }
  }
}
