package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;

@Doc("A remote config (feature flag) entry.")
public class RemoteConfig {
  @ExampleValue(ExampleValue.REMOTE_CONFIG_NAME)
  @Doc("The name of this remote config entry. These names may be prefixed with the platform type (\"android.\", "
       + "\"ios.\", \"desktop.\", etc.) Typically, clients only handle the relevant configs for its platform, "
       + "hardcoding the names it cares about handling and ignoring the rest.")
  public final String name;
  @ExampleValue(ExampleValue.REMOTE_CONFIG_VALUE)
  @Doc("The value for this remote config entry. Even though this is a string, it could be a boolean as a string, an "
       + "integer/long value, a comma-delimited list, etc. Clients usually consume this by hardcoding the feature flags"
       + "it should track in the app and assuming that the server will send the type that the client expects. If an "
       + "unexpected type occurs, it falls back to a default value.")
  public final String value;

  public RemoteConfig(String name, String value) {
    this.name = name;
    this.value = value;
  }
}
