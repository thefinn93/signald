/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.List;

public class RemoteConfigList {
  public final List<RemoteConfig> config;

  public RemoteConfigList(List<RemoteConfig> config) { this.config = config; }
}
