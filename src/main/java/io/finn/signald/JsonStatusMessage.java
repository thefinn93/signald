/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;

@Deprecated(1641027661)
public class JsonStatusMessage {
  public int msg_number;
  public String message;
  public boolean error;
  public JsonRequest request;

  JsonStatusMessage(int msgNumber, String message) {
    this.msg_number = msgNumber;
    this.message = message;
    this.error = false;
  }

  public JsonStatusMessage(int msgNumber, String message, JsonRequest request) {
    this.msg_number = msgNumber;
    this.message = message;
    this.error = true;
    this.request = request;
  }
}
