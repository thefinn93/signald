package io.finn.signald.db;

import io.finn.signald.clientprotocol.v1.JsonAddress;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface IRecipientsTable {
  String ROW_ID = "rowid";
  String ACCOUNT_UUID = "account_uuid";
  String UUID = "uuid";
  String E164 = "e164";

  Recipient get(String e164, ACI aci) throws SQLException, IOException;
  void deleteAccount(UUID uuid) throws SQLException;

  default List<Recipient> get(List<SignalServiceAddress> addresses) throws SQLException, IOException {
    List<Recipient> results = new ArrayList<>();
    for (SignalServiceAddress address : addresses) {
      results.add(get(address));
    }
    return results;
  }

  default Recipient get(SignalServiceAddress address) throws SQLException, IOException { return get(address.getNumber().orNull(), address.getAci()); }

  default Recipient get(JsonAddress address) throws IOException, SQLException { return get(address.number, address.getACI()); }

  default Recipient get(UUID query) throws IOException, SQLException { return get(ACI.from(query)); }

  default Recipient get(ACI query) throws SQLException, IOException { return get(null, query); };

  default Recipient get(String identifier) throws IOException, SQLException {
    if (identifier.startsWith("+")) {
      return get(identifier, null);
    } else {
      return get(null, ACI.from(java.util.UUID.fromString(identifier)));
    }
  }
}
