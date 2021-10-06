/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
  public static final AccountAttributes.Capabilities CAPABILITIES = new AccountAttributes.Capabilities(false, true, false, true, false, true);
}
