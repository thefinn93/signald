/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;
import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

@Deprecated(1641027661)
class JsonContactTokenDetails {
  public String token;
  public String relay;
  public String number;
  public boolean voice;
  public boolean video;

  JsonContactTokenDetails(ContactTokenDetails contact) {
    this.token = contact.getToken();
    this.relay = contact.getRelay();
    this.number = contact.getNumber();
    this.voice = contact.isVoice();
    this.video = contact.isVideo();
  }
}
