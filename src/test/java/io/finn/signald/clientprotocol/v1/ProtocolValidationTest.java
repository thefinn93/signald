/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.util.RequestUtil;
import java.lang.reflect.ParameterizedType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ProtocolValidationTest {
  @ParameterizedTest
  @MethodSource("allRequestTypes")
  void TestNoCrossVersionErrors(Class<? extends RequestType<?>> r) throws NoSuchMethodException {
    for (Class<?> exception : r.getMethod("run", Request.class).getExceptionTypes()) {
      Assertions.assertEquals(RequestUtil.getVersion(r), RequestUtil.getVersion(exception));
      Assertions.assertTrue(exception.getPackageName().startsWith("io.finn.signald"));
    }
  }

  @ParameterizedTest
  @DisplayName("errors must end with the string Error")
  @MethodSource("allRequestTypes")
  void TestErrors(Class<? extends RequestType<?>> r) throws NoSuchMethodException {
    for (Class<?> exception : r.getMethod("run", Request.class).getExceptionTypes()) {
      Assertions.assertTrue(exception.getSimpleName().endsWith("Error"), exception.getName() + " thrown by " + r.getSimpleName() + " does not end in string \"Error\"");
    }
  }

  @ParameterizedTest
  @MethodSource("allRequestTypes")
  void TestResponseTypes(Class<? extends RequestType<?>> r) {
    // ProtocolRequest can't really document itself so it's allowed to finely control it's response
    Assumptions.assumeFalse(r.equals(ProtocolRequest.class));

    Class<?> responseType = (Class<?>)((ParameterizedType)r.getGenericInterfaces()[0]).getActualTypeArguments()[0];

    // Empty is a known type that will not be documented
    Assumptions.assumeFalse(responseType.equals(Empty.class));

    // AddServerRequest returns a String. Unclear if we should continue to allow this.
    Assumptions.assumeFalse(responseType.equals(String.class));

    Assertions.assertEquals(RequestUtil.getVersion(r), RequestUtil.getVersion(responseType));
  }

  private static Stream<Arguments> allRequestTypes() { return RequestUtil.REQUEST_TYPES.stream().map(Arguments::of); }
}
