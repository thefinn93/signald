/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.whispersystems.signalservice.api.push.ACI;

public class AllIdentityKeyList {
  @JsonProperty("identity_keys") List<IdentityKeyList> identityKeys;

  public AllIdentityKeyList(Recipient self, org.signal.libsignal.protocol.IdentityKey ownKey, List<IIdentityKeysTable.IdentityKeyRow> entireIdentityDB) throws InternalError {
    Map<String, IdentityKeyList> keyMap = new HashMap<>();
    try {
      for (var row : entireIdentityDB) {
        if (!keyMap.containsKey(row.getAddress().toString())) {
          var recipient = Database.Get(ACI.from(self.getServiceId().uuid())).RecipientsTable.get(row.getAddress());
          keyMap.put(row.getAddress().toString(), new IdentityKeyList(self, ownKey, recipient, null));
        }
        keyMap.get(row.getAddress().toString()).addKey(row);
      }
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up identity keys", e);
    }
    identityKeys = new ArrayList<>(keyMap.values());
  }
}
