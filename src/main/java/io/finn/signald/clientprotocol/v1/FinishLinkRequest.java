/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.ProvisioningManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.UserAlreadyExistsException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.ACI;

@Doc("After a linking URI has been requested, finish_link must be called with the session_id provided with the URI. "
     + "it will return information about the new account once the linking process is completed by the other device "
     + "and the new account is setup. Note that the account setup process can sometimes take some time, if rapid user"
     + "feedback is required after scanning, use wait_for_scan first, then finish setup with finish_link.")
@ProtocolType("finish_link")
public class FinishLinkRequest implements RequestType<Account> {
  private static final Logger logger = LogManager.getLogger();
  @JsonProperty("device_name") public String deviceName = "signald";
  @JsonProperty("session_id") public String sessionID;

  // this doesn't work yet
  //  @Doc("overwrite existing account data if the phone number conflicts. false by default, raises an error when there "
  //       + "is a conflict")
  //  public boolean overwrite = false;
  private boolean overwrite = false;

  @Override
  public Account run(Request request)
      throws NoSuchSessionError, ServerNotFoundError, InvalidProxyError, InternalError, NoSuchAccountError, UserAlreadyExistsError, ScanTimeoutError {
    ProvisioningManager pm = ProvisioningManager.get(sessionID);
    if (pm == null) {
      throw new NoSuchSessionError();
    }
    ACI aci;
    try {
      aci = pm.finishDeviceLink(deviceName, overwrite);
      return new Account(aci);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (IOException | TimeoutException e) {
      logger.debug("scan timeout waiting for qr code scan");
      throw new ScanTimeoutError(e);
    } catch (SQLException | InvalidInputException | InvalidKeyException | UntrustedIdentityException | NoSuchAccountException e) {
      throw new InternalError("error finishing linking", e);
    } catch (UserAlreadyExistsException e) {
      throw new UserAlreadyExistsError(e);
    }
  }
}
