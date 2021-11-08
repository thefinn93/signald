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

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.ACI;

@Deprecated(1641027661)
public class JsonPreview {
  public String url;
  public String title;
  public JsonAttachment attachment;

  public JsonPreview(SignalServiceDataMessage.Preview preview, ACI aci)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    url = preview.getUrl();
    title = preview.getTitle();
    if (preview.getImage().isPresent()) {
      attachment = new JsonAttachment(preview.getImage().get(), aci);
    }
  }
}
