/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.Recipient;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.libsignal.InvalidKeyException;

@Doc("Get information about a known keys for a particular address")
@ProtocolType("get_identities")
public class GetIdentitiesRequest implements RequestType<IdentityKeyList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("address to get keys for") @Required public JsonAddress address;

  @Override
  public IdentityKeyList run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UnregisteredUserError {
    Manager m = Common.getManager(account);
    Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);
    List<IdentityKeysTable.IdentityKeyRow> identities = null;
    try {
      identities = m.getIdentities(recipient);
    } catch (SQLException | InvalidKeyException e) {
      throw new InternalError("error getting identities", e);
    }
    return new IdentityKeyList(m.getOwnRecipient(), m.getIdentity(), recipient, identities);
  }
}
