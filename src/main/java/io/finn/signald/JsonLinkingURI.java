/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import java.net.URI;

@Deprecated(1641027661)
class JsonLinkingURI {
  public URI uri;

  JsonLinkingURI(URI uri) { this.uri = uri; }
}
