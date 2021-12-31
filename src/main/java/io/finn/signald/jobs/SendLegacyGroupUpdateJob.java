/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.db.Recipient;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.GroupInfo;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class SendLegacyGroupUpdateJob implements Job {
  private final static Logger logger = LogManager.getLogger();

  private final byte[] groupId;
  private final Recipient recipient;
  private final Manager m;

  public SendLegacyGroupUpdateJob(Manager manager, byte[] g, Recipient r) {
    m = manager;
    groupId = g;
    recipient = r;
  }

  @Override
  public void run() throws GroupNotFoundException, NotAGroupMemberException, IOException, SQLException {
    AccountData accountData = m.getAccountData();
    GroupInfo g = accountData.groupStore.getGroup(groupId);
    if (g == null) {
      logger.info("received group update request for unknown group, unable to respond");
      return;
    }

    if (!g.isMember(accountData.address)) {
      logger.info("received group update request for a group we are no longer part of, unable to respond");
    }

    SignalServiceDataMessage.Builder messageBuilder = m.getGroupUpdateMessageBuilder(g);
    final List<Recipient> membersSend = new ArrayList<>();
    membersSend.add(recipient);
    m.sendMessage(messageBuilder, membersSend);
  }
}
