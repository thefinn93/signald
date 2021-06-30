/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.ProvisioningManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import org.asamk.signal.UserAlreadyExists;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.InvalidKeyException;

@Doc("After a linking URI has been requested, finish_link must be called with the session_id provided with the URI. "
     + "it will return information about the new account once the linking process is completed by the other device.")
@ProtocolType("finish_link")
public class FinishLinkRequest implements RequestType<Account> {
  @JsonProperty("device_name") public String deviceName = "signald";
  @JsonProperty("session_id") public String sessionID;

  // this doesn't work yet
  //  @Doc("overwrite existing account data if the phone number conflicts. false by default, raises an error when there "
  //       + "is a conflict")
  //  public boolean overwrite = false;
  private boolean overwrite = false;

  @Override
  public Account run(Request request)
      throws IOException, UserAlreadyExists, TimeoutException, InvalidInputException, InvalidKeyException, NoSuchAccount, SQLException, NoSuchSession {
    ProvisioningManager pm = ProvisioningManager.get(sessionID);
    if (pm == null) {
      throw new NoSuchSession();
    }
    String accountID = pm.finishDeviceLink(deviceName, overwrite);
    return new Account(Utils.getManager(accountID));
  }
}
