/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.BuildConfig;
import io.finn.signald.annotations.ExampleValue;

public class JsonVersionMessage {
  @ExampleValue("\"" + BuildConfig.NAME + "\"") public String name;
  @ExampleValue("\"" + BuildConfig.VERSION + "\"") public String version;
  @ExampleValue("\"" + BuildConfig.BRANCH + "\"") public String branch;
  @ExampleValue("\"" + BuildConfig.COMMIT + "\"") public String commit;

  public JsonVersionMessage() {
    this.name = BuildConfig.NAME;
    this.version = BuildConfig.VERSION;
    this.branch = BuildConfig.BRANCH;
    this.commit = BuildConfig.COMMIT;
  }
}
