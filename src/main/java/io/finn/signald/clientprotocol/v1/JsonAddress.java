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

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.Util;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.UUID;

public class JsonAddress {
    public String number;

    public String uuid;

    public String relay;

    public JsonAddress() {};

    public JsonAddress(String number, UUID uuid) {
        this.number = number;
        if(uuid != null) {
            this.uuid = uuid.toString();
        }
    }

    @JsonIgnore
    public SignalServiceAddress getSignalServiceAddress() {
        return new SignalServiceAddress(getUUID(), number);
    }

    public UUID getUUID() {
        if(uuid == null) {
            return null;
        }
        return UUID.fromString(uuid);
    }

    public JsonAddress(SignalServiceAddress address) {
        if(address.getNumber().isPresent()) {
            number = address.getNumber().get();
        }

        if(address.getUuid().isPresent()) {
            uuid = address.getUuid().get().toString();
        }

        if(address.getRelay().isPresent()) {
            relay = address.getRelay().get();
        }
    }

    public JsonAddress(String number) {
        this.number = number;
    }

    JsonAddress(UUID uuid) {
        this.uuid = uuid.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof JsonAddress)) return false;

        JsonAddress that = (JsonAddress)other;
        return getSignalServiceAddress().equals(that.getSignalServiceAddress());
    }

    public String toString() {
        String out = "";
        if(number == null) {
            out += "null";
        } else {
            out += number;
        }

        out += "/";

        if(uuid == null) {
            out += "null";
        } else {
            out += uuid;
        }
        if(relay != null) {
            out += " (relay " + relay + ")";
        }
        return out;
    }

    public String toRedactedString() {
        String out = "";
        if(number == null) {
            out += "null";
        } else {
            out += Util.redact(number);
        }

        out += "/";

        if(uuid == null) {
            out += "null";
        } else {
            out += Util.redact(uuid);
        }
        if(relay != null) {
            out += " (relay " + relay + ")";
        }
        return out;
    }

    @Override
    public int hashCode() {
        return getSignalServiceAddress().hashCode();
    }

    public boolean matches(SignalServiceAddress other) {
        return getSignalServiceAddress().matches(other);
    }
}
