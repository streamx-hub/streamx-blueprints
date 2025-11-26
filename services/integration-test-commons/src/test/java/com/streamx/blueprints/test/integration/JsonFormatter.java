package com.streamx.blueprints.test.integration;

import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonFormatter {

  private JsonFormatter() {
    // no instances
  }

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static String formatJson(String json) {
    try {
      JsonNode jsonNode = objectMapper.readTree(json);
      return formatJson(jsonNode);
    } catch (JsonProcessingException ex) {
      return fail(ex);
    }
  }

  public static String formatJson(JsonNode jsonNode) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    } catch (JsonProcessingException ex) {
      return fail(ex);
    }
  }

}