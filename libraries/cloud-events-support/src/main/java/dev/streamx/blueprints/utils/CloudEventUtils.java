package dev.streamx.blueprints.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventUtils {

  private static final URI DEFAULT_SOURCE = URI.create(
      ConfigProvider.getConfig().getValue("quarkus.application.name", String.class));
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private CloudEventUtils() {
    // no instance
  }

  @SuppressWarnings("unchecked")
  public static <T> T getData(CloudEvent cloudEvent, Class<T> clazz) {
    CloudEventData cloudEventData = cloudEvent.getData();

    if (cloudEventData == null) {
      throw new IllegalStateException("CloudEvent has no data");
    }

    if (cloudEventData instanceof PojoCloudEventData<?>) {
      PojoCloudEventData<?> pojoData = (PojoCloudEventData<?>) cloudEventData;

      Object value = pojoData.getValue();
      if (!clazz.isInstance(value)) {
        throw new IllegalStateException(
            "Invalid payload type: expected " + clazz.getName() +
                " but received " + value.getClass().getName()
        );
      }

      return (T) value;
    } else {
      throw new IllegalStateException(
          "Unexpected CloudEvent data type: " + cloudEventData.getClass().getName()
      );
    }
  }

  public static CloudEventBuilder builderWithJsonData(Object data) {
    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(DEFAULT_SOURCE)
        .withTime(OffsetDateTime.now(DEFAULT_ZONE))
        .withDataContentType("application/json")
        .withData(PojoCloudEventData.wrap(data, objectMapper::writeValueAsBytes));
  }

  public static CloudEventBuilder builder() {
    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(DEFAULT_SOURCE)
        .withTime(OffsetDateTime.now(DEFAULT_ZONE))
        .withoutData();
  }
}
