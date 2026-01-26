package com.streamx.blueprints.state.repository.rocksdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.ce.serialization.CloudEventDeserializer;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonDeserializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;

final class SerializationUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private static final CloudEventSerializer eventSerializer = new CloudEventJsonSerializer();
  private static final CloudEventDeserializer eventDeserializer = new CloudEventJsonDeserializer();

  private SerializationUtils() {
    // no instances
  }

  static <T> byte[] toByteArray(T value) throws Exception {
    if (value instanceof byte[] bytes) {
      return bytes;
    }

    if (value instanceof CloudEvent event) {
      return eventSerializer.serialize(event);
    }

    return objectMapper.writeValueAsBytes(value);
  }

  static <T> T fromByteArray(byte[] data, Class<T> valueClass) throws Exception {
    if (valueClass == byte[].class) {
      return (T) data;
    }

    if (CloudEvent.class.isAssignableFrom(valueClass)) {
      return (T) eventDeserializer.deserialize(data);
    }

    return objectMapper.readValue(data, valueClass);
  }
}
