package com.streamx.blueprints.data.collector.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class JsonUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public static final String PROPERTY_LEVELS_SEPARATOR = "/";

  private JsonUtils() {
    // no instance
  }

  public static Set<String> getValues(JsonNode dataJsonNode, String property) {
    Set<String> values = new LinkedHashSet<>();

    if (StringUtils.isBlank(property) || Objects.isNull(dataJsonNode)) {
      return values;
    }

    if (dataJsonNode.isArray()) {
      dataJsonNode.forEach(entry -> values.addAll(getValues(entry, property)));
      return values;
    }

    if (property.contains(PROPERTY_LEVELS_SEPARATOR)) {
      return getLeveledValues(dataJsonNode, property);
    }

    Optional.ofNullable(dataJsonNode.get(property))
        .filter(JsonNode::isValueNode)
        .ifPresent(value -> values.add(value.asText()));
    return values;
  }

  private static Set<String> getLeveledValues(JsonNode dataJsonNode, String property) {
    Set<String> values = new LinkedHashSet<>();
    String firstPart = StringUtils.substringBefore(property, PROPERTY_LEVELS_SEPARATOR);
    String rest = StringUtils.substringAfter(property, PROPERTY_LEVELS_SEPARATOR);
    JsonNode jsonNode = dataJsonNode.get(firstPart);
    if (jsonNode != null) {
      if (jsonNode.isArray()) {
        jsonNode.forEach(entry -> values.addAll(getValues(entry, rest)));
      } else {
        values.addAll(getValues(jsonNode, rest));
      }
    }
    return values;
  }

  public static boolean containsValue(JsonNode dataJsonNode, String property, String value) {
    return Optional.ofNullable(dataJsonNode)
        .map(jsonNode -> JsonUtils.getValues(jsonNode, property))
        .stream().anyMatch(v -> v.contains(value));
  }

  public static JsonNode parseToJsonNode(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
