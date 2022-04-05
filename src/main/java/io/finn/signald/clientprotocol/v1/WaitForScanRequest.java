package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Empty;
import io.finn.signald.ProvisioningManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchSessionError;
import io.finn.signald.clientprotocol.v1.exceptions.ScanTimeoutError;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Doc("An optional part of the linking process. Intended to be called after displaying the QR code, will return quickly "
     + "after the user scans the QR code. finish_link must be called after wait_for_scan returns a non-error")
@ProtocolType("wait_for_scan")
public class WaitForScanRequest implements RequestType<Empty> {
  private static final Logger logger = LogManager.getLogger();
  @JsonProperty("session_id") public String sessionID;

  @Override
  public Empty run(Request request) throws NoSuchSessionError, ScanTimeoutError, InternalError {
    ProvisioningManager pm = ProvisioningManager.get(sessionID);
    if (pm == null) {
      throw new NoSuchSessionError();
    }
    try {
      pm.waitForScan();
    } catch (TimeoutException e) {
      logger.debug("scan timeout waiting for qr code scan", e);
      throw new ScanTimeoutError(e);
    } catch (IOException e) {
      if (e.getMessage().equals("Connection closed!")) {
        throw new ScanTimeoutError(e);
      } else {
        throw new InternalError("error finishing linking", e);
      }
    }

    return new Empty();
  }
}
