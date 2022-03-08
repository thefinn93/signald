/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import java.sql.SQLException;

public class SQLError extends ExceptionWrapper {
  public SQLError(SQLException e) { super(e); }
}
