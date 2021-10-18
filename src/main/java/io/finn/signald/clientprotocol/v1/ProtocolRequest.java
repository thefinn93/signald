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

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.util.RequestUtil.REQUEST_TYPES;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.finn.signald.BuildConfig;
import io.finn.signald.Empty;
import io.finn.signald.JsonAccountList;
import io.finn.signald.annotations.*;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.util.JSONUtil;
import io.finn.signald.util.RequestUtil;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@ProtocolType("protocol")
public class ProtocolRequest implements RequestType<JsonNode> {
  private static final ObjectMapper mapper = JSONUtil.GetMapper();
  private static final Logger logger = LogManager.getLogger();

  // the version of the protocol documentation format
  public static final String docVersion = "v1";
  public static final String info = "This document describes objects that may be used when communicating with signald.";

  @Override
  public JsonNode run(Request request) throws InternalError {
    try {
      return GetProtocolDocumentation();
    } catch (JsonMappingException | NoSuchMethodException e) {
      throw new InternalError("error building protocol documentation. If you didn't cause this, please report it. " + BuildConfig.ERROR_REPORTING_URL, e);
    }
  }

  public static JsonNode GetProtocolDocumentation() throws JsonMappingException, NoSuchMethodException {
    ObjectNode actions = JsonNodeFactory.instance.objectNode();
    List<Class<?>> uncheckedTypes = new ArrayList<>();

    // some classes not referenced by any currently documented request or reply
    uncheckedTypes.add(JsonMessageEnvelope.class);
    uncheckedTypes.add(JsonAccountList.class);
    uncheckedTypes.add(IncomingMessage.class);
    uncheckedTypes.add(ListenerState.class);
    uncheckedTypes.add(WebSocketConnectionState.class);
    uncheckedTypes.add(ClientMessageWrapper.class);
    uncheckedTypes.add(io.finn.signald.clientprotocol.v0.JsonMessageEnvelope.class);
    uncheckedTypes.addAll(SubscribeRequest.IncomingMessageEncoder.getExceptions().values());

    for (Class<? extends io.finn.signald.clientprotocol.RequestType<?>> r : REQUEST_TYPES) {
      if (r == ProtocolRequest.class) {
        continue;
      }
      ProtocolType annotation = r.getAnnotation(ProtocolType.class);
      String version = RequestUtil.getVersion(r);

      ObjectNode action = JsonNodeFactory.instance.objectNode();
      action.put("request", r.getSimpleName());
      uncheckedTypes.add(r);

      logger.debug("Scanning " + r.getCanonicalName());

      Class<?> responseType = (Class<?>)((ParameterizedType)r.getGenericInterfaces()[0]).getActualTypeArguments()[0];
      if (responseType != Empty.class) {
        action.put("response", responseType.getSimpleName());
        uncheckedTypes.add(responseType);
      }

      if (r.getAnnotation(Doc.class) != null) {
        action.put("doc", r.getAnnotation(Doc.class).value());
      }

      if (r.getAnnotation(Deprecated.class) != null) {
        action.put("deprecated", true);
        action.put("removal_date", r.getAnnotation(Deprecated.class).value());
      }

      Map<String, String> errorDocs = new HashMap<>();
      for (ErrorDoc e : r.getAnnotationsByType(ErrorDoc.class)) {
        errorDocs.put(e.error().getName(), e.doc());
      }

      ArrayNode errors = JsonNodeFactory.instance.arrayNode();
      for (Class<?> exception : r.getMethod("run", Request.class).getExceptionTypes()) {
        if (!exception.getCanonicalName().startsWith("io.finn.signald")) {
          continue;
        }
        ObjectNode errorNode = JsonNodeFactory.instance.objectNode();
        errorNode.put("name", exception.getSimpleName());

        if (errorDocs.containsKey(exception.getName())) {
          errorNode.put("doc", errorDocs.get(exception.getName()));
        }

        errors.add(errorNode);
        uncheckedTypes.add(exception);
      }

      if (errors.size() > 0) {
        action.set("errors", errors);
      }

      ObjectNode versionedActions = actions.has(version) ? (ObjectNode)actions.get(version) : JsonNodeFactory.instance.objectNode();
      versionedActions.set(annotation.value(), action);
      if (!actions.has(version)) {
        actions.set(version, versionedActions);
      }
    }
    ObjectNode types = JsonNodeFactory.instance.objectNode();
    while (uncheckedTypes.size() > 0) {
      Class<?> type = uncheckedTypes.remove(0);
      if (type == null) {
        continue;
      }

      String version = RequestUtil.getVersion(type);
      if (version == null) {
        continue;
      }

      ObjectNode versionedTypes = types.has(version) ? (ObjectNode)types.get(version) : JsonNodeFactory.instance.objectNode();
      versionedTypes.set(type.getSimpleName(), scanObject(type, uncheckedTypes));
      types.set(version, versionedTypes);
    }

    ObjectNode response = JsonNodeFactory.instance.objectNode();
    response.put("doc_version", docVersion);
    response.set("version", mapper.valueToTree(new JsonVersionMessage()));
    response.put("info", info);
    response.set("types", types);
    response.set("actions", actions);
    return response;
  }

  static JsonNode scanObject(Class<?> type, List<Class<?>> types) throws JsonMappingException {
    logger.debug("scanning " + type.getCanonicalName());
    ObjectNode output = JsonNodeFactory.instance.objectNode();

    PropertyVisitorWrapper v = new PropertyVisitorWrapper(types);
    mapper.acceptJsonFormatVisitor(type, v);
    output.set("fields", v.getResponse());

    if (type.getAnnotation(Doc.class) != null) {
      output.put("doc", type.getAnnotation(Doc.class).value());
    }

    if (type.getAnnotation(Deprecated.class) != null) {
      output.put("deprecated", true);
      output.put("removal_date", type.getAnnotation(Deprecated.class).value());
    }

    if (type.getName().startsWith("io.finn.signald.clientprotocol.v1.exceptions")) {
      output.put("error", true);
    }

    return output;
  }

  public static class PropertyVisitorWrapper extends JsonFormatVisitorWrapper.Base {
    ObjectNode response = JsonNodeFactory.instance.objectNode();
    List<Class<?>> types;

    PropertyVisitorWrapper(List<Class<?>> t) { types = t; }

    public void addProperty(String key, JavaType t, Doc doc, ExampleValue exampleValue, boolean required) {
      ObjectNode property = JsonNodeFactory.instance.objectNode();

      JavaType type = t;
      if (type.isCollectionLikeType()) {
        property.put("list", true);
        type = t.getContentType();
      }

      String typeName = type.getRawClass().getSimpleName();
      if ("byte[]".equals(type.getRawClass().getSimpleName())) {
        property.put("type", "String");
      } else {
        property.put("type", typeName);
        String version = RequestUtil.getVersion(type.getRawClass());
        if (version != null) {
          property.put("version", version);
        }
      }
      if (doc != null) {
        property.put("doc", doc.value());
      }

      if (exampleValue != null) {
        property.put("example", exampleValue.value());
      }

      if (required) {
        property.put("required", true);
      }

      addType(type.getRawClass());
      response.set(key, property);
    }

    private void addType(Class<?> toAdd) {
      if (toAdd == String.class || toAdd == long.class || toAdd == UUID.class || toAdd == Long.class || toAdd == int.class || toAdd == boolean.class || toAdd == byte[].class) {
        return;
      }
      for (Class<?> t : types) {
        if (t == toAdd) {
          return;
        }
      }
      types.add(toAdd);
    }

    public ObjectNode getResponse() { return response; }

    @Override
    public JsonObjectFormatVisitor expectObjectFormat(JavaType type) {
      return new ProtocolObjectFormatVisitor(this);
    }

    public List<Class<?>> getTypes() { return types; }
  }

  public static class ProtocolObjectFormatVisitor extends JsonObjectFormatVisitor.Base {
    private final PropertyVisitorWrapper wrapper;

    public ProtocolObjectFormatVisitor(PropertyVisitorWrapper w) { wrapper = w; }

    @Override
    public void property(BeanProperty writer) {}

    @Override
    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) {}

    @Override
    public void optionalProperty(BeanProperty writer) {
      if (writer.getAnnotation(ProtocolIgnore.class) != null) {
        return;
      }
      Doc doc = writer.getAnnotation(Doc.class);
      ExampleValue example = writer.getAnnotation(ExampleValue.class);
      boolean required = writer.getAnnotation(Required.class) != null;
      wrapper.addProperty(writer.getName(), writer.getType(), doc, example, required);
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) {}
  }

  public static class DocumentedRequest {
    String type;
    Class<? extends io.finn.signald.clientprotocol.RequestType<?>> request;
    Class<?> response;

    DocumentedRequest(String type, Class<? extends io.finn.signald.clientprotocol.RequestType<?>> request, Class<?> response) {
      this.type = type;
      this.request = request;
      this.response = response;
    }
  }
}
