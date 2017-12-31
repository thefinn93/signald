package io.finn.signald;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;

class JsonAccountList {
  public List<JsonAccount> accounts = new ArrayList<JsonAccount>();

  JsonAccountList(ConcurrentHashMap<String,Manager> managers) {
    Enumeration<String> usernames = managers.keys();
    while(usernames.hasMoreElements()) {
      Manager manager = managers.get(usernames.nextElement());
      JsonAccount account = new JsonAccount(manager);
      accounts.add(account);
    }
  }
}
