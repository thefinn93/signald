/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.logging.SignalProtocolLogger;

class ProtocolLogger implements SignalProtocolLogger {
  private static final Logger logger = LogManager.getLogger("libsignal");

  public void log(int priority, String tag, String message) { logger.debug("[" + tag + "] " + message); }
}
