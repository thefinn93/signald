/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.ProvisioningManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

public class LinkingURI {
  public String uri;

  @JsonProperty("session_id") public String sessionID;

  public LinkingURI(String s, ProvisioningManager pm) throws IOException, TimeoutException, URISyntaxException {
    uri = pm.getDeviceLinkUri().toString();
    sessionID = s;
  }
}
