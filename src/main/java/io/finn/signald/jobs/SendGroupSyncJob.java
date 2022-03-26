/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.Util;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.GroupInfo;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
          Optional<Integer> expirationTimer = Optional.empty();
          Optional<String> color = Optional.empty();
          out.write(new DeviceGroup(record.groupId, Optional.ofNullable(record.name), record.getMembers(), m.createGroupAvatarAttachment(record.groupId), record.active,
                                    expirationTimer, color, false, Optional.empty(), false));
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
