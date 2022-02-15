package io.finn.signald.db;

import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.AccountIdentifier;

public class DatabaseDataStore implements SignalServiceDataStore {

  private final ACI aci;
  public DatabaseDataStore(ACI aci) { this.aci = aci; }

  @Override
  public SignalServiceAccountDataStore get(AccountIdentifier accountIdentifier) {
    return new DatabaseAccountDataStore(ACI.from(accountIdentifier.uuid()));
  }

  @Override
  public SignalServiceAccountDataStore aci() {
    return new DatabaseAccountDataStore(aci);
  }

  @Override
  public SignalServiceAccountDataStore pni() {
    return new DatabaseAccountDataStore(aci);
  }

  @Override
  public boolean isMultiDevice() {
    return aci().isMultiDevice();
  }
}
