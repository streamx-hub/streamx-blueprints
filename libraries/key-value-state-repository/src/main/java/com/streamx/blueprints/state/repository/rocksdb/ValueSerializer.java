package com.streamx.blueprints.state.repository.rocksdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.ce.serialization.CloudEventSerializer;
import com.streamx.ce.serialization.json.CloudEventJsonSerializer;
import io.cloudevents.core.v1.CloudEventV1;

final class ValueSerializer {

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final CloudEventSerializer eventSerializer = new CloudEventJsonSerializer();

  private ValueSerializer() {
    // no instances
  }

  public static <T> byte[] toByteArray(T value) throws Exception {
    if (value instanceof CloudEventV1 event) {
      return eventSerializer.serialize(event);
    }

    return objectMapper.writeValueAsBytes(value);
  }
}
