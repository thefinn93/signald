/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.SignalDependencies;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ProtocolType("get_remote_config")
@Doc("Retrieves the remote config (feature flags) from the server.")
public class RemoteConfigRequest implements RequestType<RemoteConfigList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use to retrieve the remote config") @Required public String account;

  @Override
  public RemoteConfigList run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    UUID accountUUID;
    try {
      accountUUID = Database.Get().AccountsTable.getUUID(account);
    } catch (SQLException e) {
      throw new InternalError("error getting local account UUID", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    final Map<String, Object> remoteConfig;
    try {
      remoteConfig = SignalDependencies.get(accountUUID).getAccountManager().getRemoteConfig();
    } catch (IOException | SQLException e) {
      throw new InternalError("error getting remote config", e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    final List<RemoteConfig> remoteConfigAsList = new ArrayList<>(remoteConfig.size());
    for (final Map.Entry<String, Object> entry : remoteConfig.entrySet()) {
      remoteConfigAsList.add(new RemoteConfig(entry.getKey(), String.valueOf(entry.getValue())));
    }

    return new RemoteConfigList(remoteConfigAsList);
  }
}
