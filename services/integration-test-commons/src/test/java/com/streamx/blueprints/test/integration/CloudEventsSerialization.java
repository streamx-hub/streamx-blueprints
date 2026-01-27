package com.streamx.blueprints.test.integration;

import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.vertx.core.buffer.Buffer;

public final class CloudEventsSerialization {

  private static final CloudEventJsonSerializer eventsSerializer = new CloudEventJsonSerializer();
  private static final CloudEventJsonDeserializer eventsDeserializer =
      new CloudEventJsonDeserializer();

  private CloudEventsSerialization() {
    // no instances
  }

  public static String serialize(CloudEvent cloudEvent) {
    return eventsSerializer.serialize(cloudEvent).toString();
  }

  public static CloudEvent deserialize(byte[] serializedCloudEvent) {
    return eventsDeserializer.deserialize(Buffer.buffer(serializedCloudEvent));
  }

}