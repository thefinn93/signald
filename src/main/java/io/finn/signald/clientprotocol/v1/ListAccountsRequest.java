/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;

@ProtocolType("list_accounts")
@Doc("return all local accounts")
public class ListAccountsRequest implements RequestType<AccountList> {
  @Override
  public AccountList run(Request request) throws NoSuchAccountError, InternalError {
    return new AccountList();
  }
}
