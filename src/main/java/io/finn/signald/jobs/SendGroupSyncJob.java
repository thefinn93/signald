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
import io.finn.signald.Util;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.GroupInfo;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;

public class SendGroupSyncJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final Manager m;

  public SendGroupSyncJob(Manager manager) { m = manager; }
  @Override
  public void run() throws IOException, UntrustedIdentityException, SQLException {
    File groupsFile = Util.createTempFile();
    AccountData accountData = m.getAccountData();

    try {
      try (OutputStream fos = new FileOutputStream(groupsFile)) {
        DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
        for (GroupInfo record : accountData.groupStore.getGroups()) {
          Optional<Integer> expirationTimer = Optional.absent();
          Optional<String> color = Optional.absent();
          out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name), record.getMembers(), m.createGroupAvatarAttachment(record.groupId), record.active,
                                    expirationTimer, color, false, Optional.absent(), false));
        }
      }

      if (groupsFile.exists() && groupsFile.length() > 0) {
        try (FileInputStream groupsFileStream = new FileInputStream(groupsFile)) {
          SignalServiceAttachmentStream attachmentStream =
              SignalServiceAttachment.newStreamBuilder().withStream(groupsFileStream).withContentType("application/octet-stream").withLength(groupsFile.length()).build();

          m.sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
        }
      }
    } finally {
      try {
        Files.delete(groupsFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete groups temp file " + groupsFile + ": " + e.getMessage());
      }
    }
  }
}
