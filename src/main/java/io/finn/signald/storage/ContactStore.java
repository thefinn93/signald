/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import io.finn.signald.Account;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.UnregisteredUserError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IContactsTable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContactStore {
  private static final Logger logger = LogManager.getLogger();
  public final List<IContactsTable.JsonContactInfo> contacts = new ArrayList<>();

  public boolean migrateToDB(Account account) throws SQLException, UnregisteredUserError, InternalError, IOException {
    boolean needsSave = contacts.size() > 0;
    logger.info("migrating {} contacts to the database", contacts.size());
    List<IContactsTable.ContactInfo> list = new ArrayList<>();
    for (var ci : contacts) {
      list.add(new IContactsTable.ContactInfo(account.getACI(), ci));
    }
    Database.Get(account.getACI()).ContactsTable.addBatch(list);
    return needsSave;
  }
}
