/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.Util;
import io.finn.signald.annotations.Deprecated;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

@Deprecated(1641027661)
public class JsonAddress {
  private static final Logger logger = LogManager.getLogger();

  public String number;
  public String uuid;
  public String relay;

  public JsonAddress() {}

  public JsonAddress(String n, UUID uuid) {
    if (!n.startsWith("+") && UuidUtil.isUuid(n)) {
      logger.warn("Number field has a valid UUID in it! Converting to UUID field (this is to fix a data migration "
                  + "issue in signald, do not rely on this behavior when using the socket API)");
      uuid = UUID.fromString(n);
    } else {
      number = n;
    }
    if (uuid != null) {
      this.uuid = uuid.toString();
    }
  }

  public JsonAddress(JsonAddress other) {
    number = other.number;
    uuid = other.uuid;
    relay = other.relay;
  }

  @JsonIgnore
  public SignalServiceAddress getSignalServiceAddress() {
    return new SignalServiceAddress(getACI(), number);
  }

  // unused, but required for backwards compatibility
  public UUID getUUID() { return UUID.fromString(uuid); }

  @JsonIgnore
  public ACI getACI() {
    if (uuid == null) {
      return null;
    }
    return ACI.from(UUID.fromString(uuid));
  }

  public JsonAddress(SignalServiceAddress address) {
    if (address.getNumber().isPresent()) {
      String n = address.getNumber().get();
      if (!n.startsWith("+") && UuidUtil.isUuid(n)) {
        logger.warn("Number field has a valid UUID in it! Converting to UUID field (this is to fix a data migration "
                    + "issue in signald, do not rely on this behavior when using the socket API)");
        uuid = n;
      } else {
        number = n;
      }
    }

    if (address.getServiceId() != null) {
      uuid = address.getServiceId().uuid().toString();
    }
  }

  public JsonAddress(String identifier) {
    if (identifier.startsWith("+")) {
      this.number = identifier;
    } else {
      this.uuid = identifier;
    }
  }

  public JsonAddress(UUID uuid) { this.uuid = uuid.toString(); }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof JsonAddress)) {
      return false;
    }

    JsonAddress that = (JsonAddress)other;
    return getSignalServiceAddress().equals(that.getSignalServiceAddress());
  }

  public String toString() {
    String out = "";
    if (number == null) {
      out += "null";
    } else {
      out += number;
    }

    out += "/";

    if (uuid == null) {
      out += "null";
    } else {
      out += uuid;
    }
    if (relay != null) {
      out += " (relay " + relay + ")";
    }
    return out;
  }

  public String toRedactedString() {
    String out = "";
    if (number == null) {
      out += "null";
    } else {
      out += Util.redact(number);
    }

    out += "/";

    if (uuid == null) {
      out += "null";
    } else {
      out += Util.redact(uuid);
    }
    if (relay != null) {
      out += " (relay " + relay + ")";
    }
    return out;
  }

  @Override
  public int hashCode() {
    return getSignalServiceAddress().hashCode();
  }

  public boolean matches(JsonAddress other) { return matches(other.getSignalServiceAddress()); }

  public boolean matches(SignalServiceAddress other) { return getSignalServiceAddress().matches(other); }

  public void update(SignalServiceAddress a) {
    if (uuid == null && a.getServiceId() != null) {
      uuid = a.getServiceId().uuid().toString();
    }

    if (number == null && a.getNumber().isPresent()) {
      number = a.getNumber().get();
    }
  }
}
