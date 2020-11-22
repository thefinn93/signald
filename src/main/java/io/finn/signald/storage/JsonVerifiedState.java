/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald.storage;

import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;

public class JsonVerifiedState {
  public IdentityKeyStore.Identity identity;
  public long timestamp;
  public String state;

  public JsonVerifiedState() {}

  public JsonVerifiedState(VerifiedMessage verifiedMessage) {
    identity = new IdentityKeyStore.Identity(verifiedMessage.getIdentityKey());
    timestamp = verifiedMessage.getTimestamp();
    state = verifiedMessage.getVerified().name();
  }
}
