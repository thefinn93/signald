/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.InvalidKeyException;

@ProtocolType("add_device")
@ErrorDoc(error = InvalidRequestError.class, doc = "caused by syntax errors with the provided linking URI")
@Doc("Link a new device to a local Signal account")
public class AddLinkedDeviceRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.LINKING_URI) @Doc("the sgnl://linkdevice uri provided (typically in qr code form) by the new device") @Required public String uri;

  @Override
  public Empty run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InvalidRequestError, InternalError, AuthorizationFailedError, SQLError {
    Manager m = Common.getManager(account);
    try {
      m.addDeviceLink(new URI(uri));
    } catch (IOException | InvalidKeyException e) {
      throw new InternalError("error adding device", e);
    } catch (InvalidInputException | URISyntaxException e) {
      throw new InvalidRequestError(e.getMessage());
    }
    return new Empty();
  }
}
