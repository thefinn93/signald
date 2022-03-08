/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.MessageReceiver;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.push.ACI;

@ProtocolType("unsubscribe")
@Doc("See subscribe for more info")
public class UnsubscribeRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to unsubscribe from") @Required public String account;

  @Override
  public Empty run(Request request) throws NoSuchAccountError, InternalError {
    ACI aci;
    try {
      aci = Database.Get().AccountsTable.getACI(account);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException e) {
      throw new InternalError("error getting account UUID", e);
    }

    MessageReceiver.unsubscribe(aci, request.getSocket());
    return new Empty();
  }
}
