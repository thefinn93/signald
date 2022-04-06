/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException;

public class FingerprintVersionMismatchError extends ExceptionWrapper {
  public FingerprintVersionMismatchError(FingerprintVersionMismatchException e) { super(e); }
}
