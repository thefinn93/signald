/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;

public class InvalidFingerprintError extends ExceptionWrapper {
  public InvalidFingerprintError(FingerprintParsingException e) { super(e); }
}
