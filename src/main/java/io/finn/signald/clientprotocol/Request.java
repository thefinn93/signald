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

package io.finn.signald.clientprotocol;

import static io.finn.signald.util.RequestUtil.REQUEST_TYPES;
import static io.finn.signald.util.RequestUtil.getVersion;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.JsonMessageWrapper;
import io.finn.signald.annotations.*;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.*;
import io.finn.signald.clientprotocol.v1.exceptions.ExceptionWrapper;
import io.finn.signald.clientprotocol.v1.exceptions.RequestProcessingError;
import io.finn.signald.util.JSONUtil;
import io.finn.signald.util.RequestUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Request {
  public static final Map<String, Map<String, Class<? extends RequestType<?>>>> requests = getRequests();
  public static final Map<String, String> defaultVersions = getDefaultVersions();

  private RequestType<?> requestType;
  private final String type;
  private String version;
  private String id;
  private final Socket socket;
  private Logger logger;

  private final ObjectMapper mapper = JSONUtil.GetMapper();

  public static Map<String, Map<String, Class<? extends RequestType<?>>>> getRequests() {
    Map<String, Map<String, Class<? extends RequestType<?>>>> requests = new HashMap<>();
    for (Class<? extends RequestType<?>> t : REQUEST_TYPES) {
      ProtocolType annotation = t.getDeclaredAnnotation(ProtocolType.class);
      if (!requests.containsKey(annotation.value())) {
        requests.put(annotation.value(), new HashMap<>());
      }
      Map<String, Class<? extends RequestType<?>>> versions = requests.get(annotation.value());
      versions.put(RequestUtil.getVersion(t), t);
      requests.put(annotation.value(), versions);
      requests.get(annotation.value()).put(getVersion(t), t);
    }
    return requests;
  }

  public static Map<String, String> getDefaultVersions() {
    Map<String, String> v = new HashMap<>();
    v.put(VersionRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(ProtocolRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(GetLinkedDevicesRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(RemoveLinkedDeviceRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(AcceptInvitationRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(ApproveMembershipRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(GetGroupRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(JoinGroupRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(ResolveAddressRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(CreateGroupRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(GenerateLinkingURIRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(FinishLinkRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(DeleteAccountRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(TypingRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(ResetSessionRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    v.put(RequestSyncRequest.class.getAnnotation(ProtocolType.class).value(), "v1");
    return v;
  }

  public Request(JsonNode request, Socket s) throws IOException {
    socket = s;
    initializeMapper();

    if (request.has("id")) {
      id = request.get("id").asText();
    }

    List<String> problems = new ArrayList<>();
    if (!request.has("type")) {
      problems.add("missing required argument: type");
    }

    type = request.get("type").asText();

    Thread.currentThread().setName(id != null ? id + "-" + type : type);

    if (request.has("version")) {
      version = request.get("version").asText();
    } else if (defaultVersions.containsKey(type)) {
      version = defaultVersions.get(type);
    } else {
      problems.add("missing required argument: version");
    }

    if (problems.size() > 0) {
      error(new RequestValidationFailure(problems));
      return;
    }

    logger = LogManager.getLogger(type);

    if (!requests.containsKey(type)) {
      error(new RequestValidationFailure("Unknown request type: " + type));
      return;
    }

    if (!requests.get(type).containsKey(version)) {
      error(new RequestValidationFailure("unknown version of that request type"));
      return;
    }

    Class<? extends RequestType<?>> requestClass = requests.get(type).get(version);

    if (requestClass.getAnnotation(Deprecated.class) != null) {
      logger.warn("{} {} is deprecated and will be removed in a future version of signald. please update your client", type, version);
    }

    requestType = mapper.convertValue(request, requestClass);
    List<String> validationFailures = validate(request);
    if (validationFailures.size() > 0) {
      logger.warn("invalid request");
      error(new RequestValidationFailure(validationFailures));
      return;
    }

    try {
      Object r = requestType.run(this);
      reply(new JsonMessageWrapper(type, r, id));
      if (id != null) {
        logger.info("handled request {} successfully", id);
      }
    } catch (ExceptionWrapper e) {
      error(e);
      if (e.isUnexpected()) {
        logger.error("error while handling request", e);
      }
    } catch (Throwable throwable) {
      error(new RequestProcessingError(throwable));
      logger.error("error while handling request", throwable);
    }
  }

  private List<String> validate(JsonNode request) {
    List<String> errors = new ArrayList<>();
    HashMap<String, Integer> exactlyOneOfRequired = new HashMap<>();

    for (Field f : requestType.getClass().getFields()) {
      // Field does not exist in request
      if (!request.has(getName(f))) {
        if (f.getAnnotation(Required.class) != null || f.getAnnotation(RequiredNonEmpty.class) != null) {
          errors.add("missing required argument: " + getName(f));
        }
        if (f.getAnnotation(AtLeastOneOfRequired.class) != null) {
          AtLeastOneOfRequired requirement = f.getAnnotation(AtLeastOneOfRequired.class);
          int found = 0;
          for (String option : requirement.value()) {
            if (request.has(option)) {
              found++;
            }
          }
          if (found == 0) {
            errors.add("at least one required of: " + getName(f) + " or " + String.join(" or ", requirement.value()));
          }
        }
      } else { // argument is present
        if (f.getAnnotation(RequiredNonEmpty.class) != null) {
          JsonNode field = request.get(getName(f));
          if (field.isArray()) {
            if (field.size() == 0) {
              errors.add(getName(f) + " must have at least 1 entry");
            }
          }
        }
      }

      if (f.getAnnotation(ExactlyOneOfRequired.class) != null) {
        ExactlyOneOfRequired requirement = f.getAnnotation(ExactlyOneOfRequired.class);
        String key = requirement.value();

        Integer value = 0;
        if (exactlyOneOfRequired.containsKey(key)) {
          value = exactlyOneOfRequired.get(key);
        }

        if (request.has(getName(f))) {
          value++;
        }

        exactlyOneOfRequired.put(key, value);
      }
    }

    for (Map.Entry<String, Integer> entry : exactlyOneOfRequired.entrySet()) {
      if (entry.getValue() != 1) {
        List<String> allOptions = new ArrayList<>();
        for (Field f : requestType.getClass().getFields()) {
          ExactlyOneOfRequired requirement = f.getAnnotation(ExactlyOneOfRequired.class);
          if (requirement != null && requirement.value().equals(entry.getKey())) {
            allOptions.add(getName(f));
          }
        }
        errors.add("exactly one required of: " + String.join(", ", allOptions) + " (" + entry.getValue() + " found)");
      }
    }

    return errors;
  }

  private void error(Object data) throws IOException { reply(JsonMessageWrapper.error(type, data, id)); }

  private void reply(JsonMessageWrapper message) throws IOException {
    try {
      new PrintWriter(getSocket().getOutputStream(), true).println(mapper.writeValueAsString(message));
    } catch (SocketException e) {
      logger.warn("Could not send reply: {}", e.getMessage());
    }
  }

  public Socket getSocket() { return socket; }

  private void initializeMapper() {
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setSerializationInclusion(Include.NON_NULL);
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  private String getName(Field f) {
    if (f.getAnnotation(JsonProperty.class) != null && !f.getAnnotation(JsonProperty.class).value().equals("")) {
      return f.getAnnotation(JsonProperty.class).value();
    }
    return f.getName();
  }
}
