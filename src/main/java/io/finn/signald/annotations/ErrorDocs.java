/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/* ErrorDocs is an annotation that gets created by having multiple ErrorDoc annotations
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorDocs {
  ErrorDoc[] value();
}
