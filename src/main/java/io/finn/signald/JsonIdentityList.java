/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidAddressException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.libsignal.InvalidKeyException;

@Deprecated(1641027661)
class JsonIdentityList {
  public List<JsonIdentity> identities = new ArrayList<>();

  JsonIdentityList(List<IIdentityKeysTable.IdentityKeyRow> identities, Manager m) throws IOException, SQLException {
    for (var identity : identities) {
      this.identities.add(new JsonIdentity(identity, m));
    }
  }

  JsonIdentityList(Recipient recipient, Manager m) throws SQLException, InvalidKeyException, InvalidAddressException, IOException {
    if (recipient == null) {
      for (var identity : m.getIdentities()) {
        this.identities.add(new JsonIdentity(identity, m));
      }
    } else {
      var identities = m.getIdentities(recipient);
      if (identities != null) {
        for (var identity : identities) {
          this.identities.add(new JsonIdentity(identity, m, recipient));
        }
      }
    }
  }
}
