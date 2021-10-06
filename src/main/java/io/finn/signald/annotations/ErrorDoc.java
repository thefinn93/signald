/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.annotations;

import io.finn.signald.clientprotocol.v1.exceptions.ExceptionWrapper;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ErrorDocs.class)
public @interface ErrorDoc {
  Class<? extends ExceptionWrapper> error();
  String doc();
}
