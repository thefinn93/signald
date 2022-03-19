/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Empty;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

@ProtocolType("submit_challenge")
public class SubmitChallengeRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Required public String account;
  @Required public String challenge;
  @JsonProperty("captcha_token") public String captchaToken;

  @Override
  public Empty run(Request request) throws NoSuchAccountError, InvalidRequestError, ServerNotFoundError, InvalidProxyError, InternalError, SQLError {
    SignalServiceAccountManager accountManager;
    try {
      accountManager = Common.getAccount(account).getSignalDependencies().getAccountManager();
    } catch (IOException e) {
      throw new InternalError("error getting signal dependencies", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException e) {
      throw new SQLError(e);
    }

    try {
      if (captchaToken != null) {
        accountManager.submitRateLimitRecaptchaChallenge(challenge, captchaToken);
      } else {
        accountManager.submitRateLimitPushChallenge(challenge);
      }
    } catch (IOException e) {
      throw new InternalError("error submitting challenge", e);
    }

    return new Empty();
  }
}
