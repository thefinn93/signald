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

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidMessageException;
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
