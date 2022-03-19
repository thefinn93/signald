/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ExactlyOneOfRequired {

  String value();

  String RECIPIENT = "recipient";
  String GROUP_MODIFICATION = "group-modification";
  String SYNC_REQUEST = "sync-request";
  String ACCOUNT = "account";
}
