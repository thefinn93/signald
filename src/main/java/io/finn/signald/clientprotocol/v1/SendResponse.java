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

import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

import java.util.List;
import java.util.stream.Collectors;

public class SendResponse {
  public List<JsonSendMessageResult> results;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;

  public SendResponse(List<SendMessageResult> r, long t) {
    results = r.stream().map(JsonSendMessageResult::new).collect(Collectors.toList());
    timestamp = t;
  }
}