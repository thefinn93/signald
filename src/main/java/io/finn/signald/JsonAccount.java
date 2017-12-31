package io.finn.signald;

class JsonAccount {
  public int deviceId;
  public String username;
  public String filename;
  public boolean registered;
  public boolean has_keys;

  JsonAccount(Manager m) {
    this.username = m.getUsername();
    this.deviceId = m.getDeviceId();
    this.filename = m.getFileName();
    this.registered = m.isRegistered();
    this.has_keys = m.userHasKeys();
  }

}
