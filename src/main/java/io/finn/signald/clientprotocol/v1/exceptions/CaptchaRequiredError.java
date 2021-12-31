/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class CaptchaRequiredError extends ExceptionWrapper {
  public final String more = "https://signald.org/articles/captcha/";

  public CaptchaRequiredError() { super("a captcha token is required to register"); }
}
