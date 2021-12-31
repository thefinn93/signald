/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

public class SendSuccess {
  public final boolean unidentified;
  public final boolean needsSync;
  public final long duration;
  public final List<Integer> devices;

  public SendSuccess(SendMessageResult.Success success) {
    unidentified = success.isUnidentified();
    needsSync = success.isNeedsSync();
    duration = success.getDuration();
    devices = success.getDevices();
  }
}
