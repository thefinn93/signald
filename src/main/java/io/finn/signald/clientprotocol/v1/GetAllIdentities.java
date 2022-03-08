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
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidKeyException;

@Doc("get all known identity keys")
@ProtocolType("get_all_identities")
public class GetAllIdentities implements RequestType<AllIdentityKeyList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Override
  public AllIdentityKeyList run(Request request) throws InvalidProxyError, NoSuchAccountError, ServerNotFoundError, InternalError, AuthorizationFailedError, SQLError {
    Manager m = Common.getManager(account);
    try {
      return new AllIdentityKeyList(m.getOwnRecipient(), m.getIdentity(), m.getIdentities());
    } catch (SQLException | InvalidKeyException e) {
      throw new InternalError("error getting identity list", e);
    }
  }
}
