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

package io.finn.signald.db;

import io.finn.signald.clientprotocol.v0.JsonAddress;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Recipient {
  private UUID account;
  private int id;
  private SignalServiceAddress address;

  public Recipient(UUID account, int id, SignalServiceAddress address) {
    this.account = account;
    this.id = id;
    this.address = address;
  }

  public int getId() { return id; }

  public SignalServiceAddress getAddress() { return address; }

  public String toRedactedString() { return new JsonAddress(address).toRedactedString(); }

  public UUID getUUID() { return address.getUuid(); }

  public boolean equals(Recipient other) { return other.getId() == getId(); }

  // getTable returns a RecipientTable for the same account as the Recipient
  public RecipientsTable getTable() { return new RecipientsTable(account); }
}
