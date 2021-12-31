/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.Map;

public class ServerCDN {
  public int number;
  public String url;

  public ServerCDN(Map.Entry<Integer, String> entry) {
    number = entry.getKey();
    url = entry.getValue();
  }
}
