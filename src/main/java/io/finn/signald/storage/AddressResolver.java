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

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collection;

// Based on signal-cli's SignalServiceAddressResolver
public interface AddressResolver {

    /**
     * Get a SignalServiceAddress with number and/or uuid from an identifier name.
     *
     * @param identifier can be either a serialized uuid or a e164 phone number
     * @return the full address
     */
    SignalServiceAddress resolve(String identifier);

    /**
     * Attempt to create a full SignalServiceAddress with number and uuid from an address that may only have one of them
     * @param partial the partial address. If the partial has a full set of identifiers it will be returned unmodified
     * @return the full address
     */
    SignalServiceAddress resolve(SignalServiceAddress partial);

    /**
     * Attempt to resolve a collection of addresses
     * @param partials a list of addresses to resolve
     * @return a list of resolved addresses
     */
    Collection<SignalServiceAddress> resolve(Collection<SignalServiceAddress> partials);
}