package com.streamx.blueprints.data.collector.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class JsonUtils {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public static final String PROPERTY_LEVELS_SEPARATOR = "/";

  private JsonUtils() {
    // no instance
  }

  public static String[] getValues(JsonNode dataJsonNode, String property) {
    if (StringUtils.isBlank(property) || Objects.isNull(dataJsonNode)) {
      return ArrayUtils.EMPTY_STRING_ARRAY;
    }

    Set<String> result = new HashSet<>();
    if (StringUtils.isNotBlank(property)) {
      if (dataJsonNode.isArray()) {
        dataJsonNode.forEach(entry -> Optional.ofNullable(entry)
            .map(e -> getValues(e, property))
            .ifPresent(values -> result.addAll(Arrays.asList(values))));
      } else if (property.contains(PROPERTY_LEVELS_SEPARATOR)) {
        String firstPart = StringUtils.substringBefore(property, PROPERTY_LEVELS_SEPARATOR);
        String rest = StringUtils.substringAfter(property, PROPERTY_LEVELS_SEPARATOR);
        JsonNode jsonNode = dataJsonNode.get(firstPart);
        if (Objects.isNull(jsonNode)) {
          return ArrayUtils.EMPTY_STRING_ARRAY;
        }
        if (jsonNode.isArray()) {
          jsonNode.forEach(entry -> Optional.ofNullable(entry)
              .map(e -> getValues(e, rest))
              .ifPresent(values -> result.addAll(Arrays.asList(values))));
        } else {
          Optional.of(jsonNode)
              .map(e -> getValues(e, rest))
              .ifPresent(values -> result.addAll(Arrays.asList(values)));
        }
      } else {
        Optional.of(dataJsonNode)
            .map(jsonNode -> jsonNode.get(property))
            .filter(JsonNode::isValueNode)
            .ifPresent(value -> result.add(value.asText()));
      }
    }
    return result.toArray(new String[0]);
  }

  public static boolean containsValue(JsonNode dataJsonNode, String property, String value) {
    return Optional.ofNullable(dataJsonNode)
        .map(jsonNode -> JsonUtils.getValues(jsonNode, property))
        .stream().anyMatch(v -> ArrayUtils.contains(v, value));
  }

  public static JsonNode parseToJsonNode(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
