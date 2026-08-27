package com.streamx.blueprints.sql.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SerializationUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );


  public SerializationUtils() {}

  public static <T> T deserializeValue(String value, Class<T> clazz) {
    if (value == null) {
      return null;
    }

    try {
      return MAPPER.readValue(value, clazz);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to deserialize value from JSON: " + value,
          e
      );
    }
  }

  public static String serializeValue(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize value to JSON", e);
    }
  }

}
