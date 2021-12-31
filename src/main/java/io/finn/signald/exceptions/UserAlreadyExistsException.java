/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;

public class UserAlreadyExistsException extends Exception {
  private final ACI aci;
  public UserAlreadyExistsException(ACI aci) {
    super("a user with that UUID is already registered");
    this.aci = aci;
  }

  public ACI getACI() { return aci; }
}
