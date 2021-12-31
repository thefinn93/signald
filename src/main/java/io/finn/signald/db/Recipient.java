/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.clientprotocol.v0.JsonAddress;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;
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

  public UUID getUUID() { return address.getAci().uuid(); }

  public ACI getACI() { return address.getAci(); }

  public boolean equals(Recipient other) { return other.getId() == getId(); }

  // getTable returns a RecipientTable for the same account as the Recipient
  public RecipientsTable getTable() { return new RecipientsTable(account); }
}
