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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.BuildConfig;
import io.finn.signald.ProvisioningManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@ProtocolType("generate_linking_uri")
@Doc("Generate a linking URI. Typically this is QR encoded and scanned by the primary device. Submit the returned session_id with a finish_link request.")
public class GenerateLinkingURIRequest implements RequestType<LinkingURI> {
  @Doc("The identifier of the server to use. Leave blank for default (usually Signal production servers but configurable at build time)")
  public String server = BuildConfig.DEFAULT_SERVER_UUID;

  @Override
  public LinkingURI run(Request request) throws IOException, TimeoutException, URISyntaxException, SQLException, ServerNotFoundException, InvalidProxyException {
    return ProvisioningManager.create(UUID.fromString(server));
  }
}
