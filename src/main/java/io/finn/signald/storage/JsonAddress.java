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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.UUID;

public class JsonAddress {
    public String number;

    public String uuid;

    @JsonIgnore
    public SignalServiceAddress getSignalServiceAddress() {
        return new SignalServiceAddress(getUUID(), number);
    }

    @JsonIgnore
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
    }

    public JsonAddress(String number) {
        this.number = number;
    }

    JsonAddress(UUID uuid) {
        this.uuid = uuid.toString();
    }
}
