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
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.clientprotocol.v1.JsonQuote;
import io.finn.signald.clientprotocol.v1.JsonReaction;
import io.finn.signald.storage.ContactStore;

import java.util.List;

@Deprecated(1641027661)
public class JsonRequest {
  public String type;
  @Doc("the request type") public String id;
  @Doc("if included in a request, all responses will also include an id field with the same value") public String username;
  @Doc("the full e164 phone number of the account being interacted with") public String messageBody;
  @Doc("the text of the outbound message") public String recipientGroupId;
  @Doc("the group ID to send to or interact with") public JsonAddress recipientAddress;
  @Doc("the address of the remote user to send to or interact with") public Boolean voice;
  public String code;
  public String deviceName;
  public List<JsonAttachment> attachments;
  public String uri;
  public String groupName;
  public List<String> members;
  public String avatar;
  public JsonQuote quote;
  public int expiresInSeconds;
  @Doc("override the expiration time of the message. If left blank signald will use the correct expiration time for the thread") public String fingerprint;
  public String trustLevel;
  public ContactStore.ContactInfo contact;
  public String captcha;
  public String name;
  public List<Long> timestamps;
  @Doc("a list of timestamps being marked as read") public long when;
  @Doc("the timestamp, for some types of requests") public JsonReaction reaction;
  public Long timestamp;
  @Doc("the timestamp, for other types of requests") public String version = "v0";

  JsonRequest() {}
}
