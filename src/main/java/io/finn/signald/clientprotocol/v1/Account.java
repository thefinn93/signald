/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.RegistrationManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.sentry.Sentry;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;

@Doc("A local account in signald")
public class Account {
  private static final Logger logger = LogManager.getLogger();

  @Doc("The Signal device ID. Official Signal mobile clients (iPhone and Android) have device ID = 1, while linked devices such as "
       + "Signal Desktop or Signal iPad have higher device IDs.")
  @JsonProperty("device_id")
  public int deviceID;
  @Doc("The primary identifier on the account, included with all requests to signald for this account. Previously called 'username'")
  @JsonProperty("account_id")
  public String accountID;

  @Doc("The address of this account") public JsonAddress address;

  @Doc("indicates the account has not completed registration") public Boolean pending;

  public String pni;

  public Account(UUID accountUUID) throws NoSuchAccountError, InternalError { this(ACI.from(accountUUID)); }

  public Account(ACI aci) throws NoSuchAccountError, InternalError {
    try {
      try {
        accountID = Database.Get().AccountsTable.getE164(aci);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      }
      deviceID = Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.DEVICE_ID);
    } catch (SQLException e) {
      throw new InternalError("error resolving account e164", e);
    }
    address = new JsonAddress(accountID, aci);

    try {
      PNI realPNI = new io.finn.signald.Account(aci).getPNI();
      if (realPNI != null) {
        pni = realPNI.toString();
      }
    } catch (SQLException e) {
      logger.error("unexpected error fetching account PNI", e);
      Sentry.captureException(e);
    }
  }

  public Account(RegistrationManager m) {
    accountID = m.getE164();
    pending = true;
  }
}
