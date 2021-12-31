/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import java.io.IOException;

public class InvalidAttachmentError extends ExceptionWrapper {
  public String filename;
  public InvalidAttachmentError(String filename, IOException e) {
    super(e.getMessage());
    this.filename = filename;
  }
}
