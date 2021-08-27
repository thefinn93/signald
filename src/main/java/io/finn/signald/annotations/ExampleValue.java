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

package io.finn.signald.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/*
 * ExampleValue is a sample value, JSON encoded (ie. strings are quoted).
 * common values are also provided.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ExampleValue {
  String value();

  String LOCAL_PHONE_NUMBER = "\"+12024561414\"";
  String REMOTE_PHONE_NUMBER = "\"+13215551234\"";
  String REMOTE_UUID = "\"aeed01f0-a234-478e-8cf7-261c283151e7\"";
  String GROUP_ID = "\"EdSqI90cS0UomDpgUXOlCoObWvQOXlH5G3Z2d3f4ayE=\"";
  String MESSAGE_ID = "1615576442475";
  String LOCAL_EXTERNAL_JPG = "\"/tmp/image.jpg\"";
  String GROUP_TITLE = "\"Parkdale Run Club\"";
  String GROUP_DESCRIPTION = "\"A club for running in Parkdale\"";
  String MESSAGE_BODY = "\"hello\"";
  String QUOTED_MESSAGE_BODY = "\"hey ￼ what's up?\"";
  String LOCAL_UUID = "\"0cc10e61-d64c-4dbc-b51c-334f7dd45a4a\"";
  String LOCAL_GROUP_AVATAR_PATH = "\"/var/lib/signald/avatars/group-EdSqI90cS0UomDpgUXOlCoObWvQOXlH5G3Z2d3f4ayE=\"";
  String GROUP_JOIN_URI = "\"https://signal.group/#CjQKINH_GZhXhfifTcnBkaKTNRxW-hHKnGSq-cJNyPVqHRp8EhDUB7zjKNEl0NaULhsqJCX3\"";
  String LINKING_URI = "\"tsdevice:/?uuid=jAaZ5lxLfh7zVw5WELd6-Q&pub_key=BfFbjSwmAgpVJBXUdfmSgf61eX3a%2Bq9AoxAVpl1HUap9\"";
  String SAFETY_NUMBER = "\"373453558586758076680580548714989751943247272727416091564451\"";
  String REMOTE_CONFIG_NAME = "desktop.mediaQuality.levels";
  String REMOTE_CONFIG_VALUE = "1:2,61:2,81:2,82:2,65:2,31:2,47:2,41:2,32:2,385:2,971:2,974:2,49:2,33:2,*:1";
}
