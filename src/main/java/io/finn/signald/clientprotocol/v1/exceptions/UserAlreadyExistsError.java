/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.exceptions.UserAlreadyExistsException;
import java.util.UUID;

public class UserAlreadyExistsError extends ExceptionWrapper {
  private final UUID uuid;

  public UUID getUuid() { return uuid; }
  public UserAlreadyExistsError(UserAlreadyExistsException e) {
    super("a user with that UUID is already registered");
    this.uuid = e.getACI().uuid();
  }
}
