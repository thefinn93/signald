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

import io.finn.signald.Empty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.zkgroup.InvalidInputException;

@ProtocolType("set_profile")
public class SetProfile implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The phone number of the account to use") @Required public String account;

  @ExampleValue("\"signald user\"") @Doc("New profile name. Set to empty string for no profile name") @Required public String name;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) @Doc("Path to new profile avatar file, if the avatar should be updated") public String avatarFile;

  public String about;

  public String emoji;

  @Override
  public Empty run(Request request) throws IOException, NoSuchAccount, InvalidInputException, SQLException {
    File avatar = avatarFile == null ? null : new File(avatarFile);
    Utils.getManager(account).setProfile(name, avatar, about, emoji);
    return new Empty();
  }
}
