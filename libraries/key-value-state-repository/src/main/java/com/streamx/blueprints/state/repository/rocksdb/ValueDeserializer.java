package com.streamx.blueprints.state.repository.rocksdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.ce.serialization.CloudEventDeserializer;
import com.streamx.ce.serialization.json.CloudEventJsonDeserializer;
import io.cloudevents.CloudEvent;

final class ValueDeserializer {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final CloudEventDeserializer eventDeserializer = new CloudEventJsonDeserializer();

  private ValueDeserializer() {
    // no instances
  }

  public static <T> T fromByteArray(byte[] data, Class<T> valueClass) throws Exception {
    if (CloudEvent.class.isAssignableFrom(valueClass)) {
      return (T) eventDeserializer.deserialize(data);
    }

    return objectMapper.readValue(data, valueClass);
  }
}
