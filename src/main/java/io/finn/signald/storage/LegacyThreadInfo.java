/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
@Deprecated
public class LegacyThreadInfo {
  @JsonProperty public String id;

  @JsonProperty public int messageExpirationTime;
}
