package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.whispersystems.libsignal.InvalidKeyException;

@ProtocolType("get_remote_config")
@Doc("Retrieves the remote config (feature flags) from the server.")
public class RemoteConfigRequest implements RequestType<RemoteConfigList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use to retrieve the remote config") @Required public String account;

  @Override
  public RemoteConfigList run(Request request) throws NoSuchAccount, SQLException, IOException, ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    final Manager m = Utils.getManager(account);
    final Map<String, Object> remoteConfig = m.getAccountManager().getRemoteConfig();

    final List<RemoteConfig> remoteConfigAsList = new ArrayList<>(remoteConfig.size());
    for (final Map.Entry<String, Object> entry : remoteConfig.entrySet()) {
      remoteConfigAsList.add(new RemoteConfig(entry.getKey(), String.valueOf(entry.getValue())));
    }

    return new RemoteConfigList(remoteConfigAsList);
  }
}
