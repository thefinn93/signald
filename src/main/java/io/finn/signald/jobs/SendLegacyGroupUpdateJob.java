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
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.GroupInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SendLegacyGroupUpdateJob implements Job {
  private final static Logger logger = LogManager.getLogger();

  private final byte[] groupId;
  private final SignalServiceAddress recipient;
  private final Manager m;

  public SendLegacyGroupUpdateJob(Manager manager, byte[] g, SignalServiceAddress r) {
    m = manager;
    groupId = g;
    recipient = r;
  }

  @Override
  public void run() throws GroupNotFoundException, NotAGroupMemberException, IOException {
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
    final List<SignalServiceAddress> membersSend = new ArrayList<>();
    membersSend.add(recipient);
    m.sendMessage(messageBuilder, membersSend);
  }
}
