/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.clientprotocol.v0.JsonAddress;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class Recipient {
  private UUID account;
  private int id;
  private SignalServiceAddress address;
  private boolean registered;

  public Recipient(UUID account, int id, SignalServiceAddress address, boolean registered) {
    this.account = account;
    this.id = id;
    this.address = address;
    this.registered = registered;
  }

  public Recipient(UUID account, int id, SignalServiceAddress address) { this(account, id, address, true); }

  public int getId() { return id; }

  public SignalServiceAddress getAddress() { return address; }

  public String toRedactedString() { return new JsonAddress(address).toRedactedString(); }

  public UUID getUUID() { return address.getServiceId().uuid(); }

  public ServiceId getServiceId() { return address.getServiceId(); }

  public boolean isRegistered() { return registered; }

  public boolean equals(Recipient other) { return other.getId() == getId(); }
}
