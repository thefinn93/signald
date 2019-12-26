/**
 * Copyright (C) 2019 Finn Herzfeld
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

import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import java.io.IOException;

class JsonProfile {
  public String name;
  public String avatar;
  public String unrestrictedUnidentifiedAccess;

  JsonProfile(SignalServiceProfile p, byte[] profileKey) throws IOException, InvalidCiphertextException {
    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    name = new String(profileCipher.decryptName(Base64.decode(p.getName())));
  }
}
