/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;

public class DownloadStickerJob implements Job {
  Manager manager;
  SignalServiceDataMessage.Sticker sticker;

  public DownloadStickerJob(Manager m, SignalServiceDataMessage.Sticker s) {
    manager = m;
    sticker = s;
  }

  @Override
  public void run() throws IOException, SQLException, NoSuchAccountException, MissingConfigurationException, InvalidMessageException {
    File stickerFile = Manager.getStickerFile(sticker);
    Manager.createPrivateDirectories(stickerFile.getParentFile().toString());
    manager.retrieveAttachment(sticker.getAttachment().asPointer(), stickerFile, false);
  }

  public boolean needsDownload() { return !Manager.getStickerFile(sticker).exists(); }
}
