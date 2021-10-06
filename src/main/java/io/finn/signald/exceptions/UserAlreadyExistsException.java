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

package io.finn.signald.exceptions;

import java.util.UUID;

public class UserAlreadyExistsException extends Exception {
  private final UUID uuid;
  public UserAlreadyExistsException(UUID uuid) {
    super("a user with that UUID is already registered");
    this.uuid = uuid;
  }

  public UUID getUuid() { return uuid; }
}
