/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

import java.io.IOException;

public class InvalidStorageFileException extends IOException {
  public InvalidStorageFileException(String message) { super("Failed to load account data: " + message); }
}
