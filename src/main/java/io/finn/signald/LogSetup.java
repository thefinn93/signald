/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.OkHttpClient;

public class LogSetup {
  public static void setup() { Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE); }
}
