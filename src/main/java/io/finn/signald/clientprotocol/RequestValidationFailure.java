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

package io.finn.signald.clientprotocol;

import java.util.ArrayList;
import java.util.List;

public class RequestValidationFailure extends Exception {
  public List<String> validationResults;
  public final String type = "invalid_request";
  public final String message = "input validation failed, please check the request and try again.";

  public RequestValidationFailure(List<String> p) { validationResults = p; }

  public RequestValidationFailure(String p) {
    validationResults = new ArrayList<>();
    validationResults.add(p);
  }
}
