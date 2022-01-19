/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import org.whispersystems.signalservice.api.account.AccountAttributes;

public class ServiceConfig {
  public final static int PREKEY_MINIMUM_COUNT = 20;
  public final static int PREKEY_BATCH_SIZE = 100;
  public final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
  public final static long MAX_ENVELOPE_SIZE = 0;
  public final static long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = 10 * 1024 * 1024;
  public final static boolean AUTOMATIC_NETWORK_RETRY = true;
  public static final AccountAttributes.Capabilities CAPABILITIES = new AccountAttributes.Capabilities(false, true, false, true, false, true, false);
}
