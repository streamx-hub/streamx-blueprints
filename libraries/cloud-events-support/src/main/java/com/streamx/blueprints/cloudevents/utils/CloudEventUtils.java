package com.streamx.blueprints.cloudevents.utils;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.lang.Nullable;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventUtils {

  public static final String NAMESPACE_SEPARATOR = ":";

  private static final String PUBLISH_TYPE_SEARCH_TERM = ".published.";

  private static final String UNPUBLISH_TYPE_SEARCH_TERM = ".unpublished.";

  private static final URI DEFAULT_SOURCE = URI.create(
      ConfigProvider.getConfig().getValue("quarkus.application.name", String.class));
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private CloudEventUtils() {
    // no instance
  }

  @Nullable
  public static <T> T getData(CloudEvent cloudEvent, Class<T> clazz) {
    CloudEventData cloudEventData = cloudEvent.getData();

    if (cloudEventData == null) {
      return null;
    }

    if (cloudEventData instanceof JsonCloudEventData jsonData) {
      return parseJsonCloudEventData(jsonData, clazz);
    }

    if (cloudEventData instanceof PojoCloudEventData<?> pojoData) {
      return parsePojoCloudEventData(pojoData, clazz);
    }

    throw new IllegalStateException(
        "Unexpected CloudEvent data type: " + cloudEventData.getClass().getName()
    );
  }

  public static <T> T getDataOrThrow(CloudEvent cloudEvent, Class<T> clazz) {
    return Objects.requireNonNull(getData(cloudEvent, clazz));
  }

  public static String getSubject(CloudEvent cloudEvent) {
    return Objects.requireNonNull(cloudEvent.getSubject());
  }

  private static <T> T parseJsonCloudEventData(JsonCloudEventData jsonData, Class<T> clazz) {
    try {
      return objectMapper.treeToValue(jsonData.getNode(), clazz);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Error parsing payload to " + clazz.getName(), ex);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T parsePojoCloudEventData(PojoCloudEventData<?> pojoData, Class<T> clazz) {
    Object value = pojoData.getValue();
    if (!clazz.isInstance(value)) {
      throw new IllegalStateException("Invalid payload type: expected %s but received %s".formatted(
          clazz.getName(), value.getClass().getName())
      );
    }

    return (T) value;
  }

  public static boolean isPublishingType(String type) {
    return type.contains(PUBLISH_TYPE_SEARCH_TERM);
  }

  public static boolean isUnpublishingType(String type) {
    return type.contains(UNPUBLISH_TYPE_SEARCH_TERM);
  }

  public static Optional<String> getSubjectNamespace(String subject) {
    String value = requireNonNull(subject);

    var indexOfSeparator = value.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfSeparator > 0) {
      return Optional.of(value.substring(0, indexOfSeparator));
    }
    return Optional.empty();
  }

  public static String getSubjectWithoutNamespace(String subject) {
    String value = requireNonNull(subject);

    var indexOfSeparator = value.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfSeparator == 0) {
      return value.substring(NAMESPACE_SEPARATOR.length());
    } else if (indexOfSeparator > 0) {
      return value.substring(indexOfSeparator + NAMESPACE_SEPARATOR.length());
    }
    return subject;
  }

  public static CloudEvent eventWithData(String subject, String type, Object data) {
    return eventWithData(subject, type, data, getNow());
  }

  public static CloudEvent eventWithData(String subject, String type, Object data,
      OffsetDateTime time) {
    return baseBuilder(subject, type, time)
        .withDataContentType("application/json")
        .withData(PojoCloudEventData.wrap(data, objectMapper::writeValueAsBytes))
        .build();
  }

  public static CloudEvent eventWithoutData(String subject, String type) {
    return eventWithoutData(subject, type, getNow());
  }

  public static CloudEvent eventWithoutData(String subject, String type, OffsetDateTime time) {
    return baseBuilder(subject, type, time)
        .build();
  }

  private static io.cloudevents.core.v1.CloudEventBuilder baseBuilder(String subject, String type,
      OffsetDateTime time) {
    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(DEFAULT_SOURCE)
        .withSubject(subject)
        .withType(type)
        .withTime(time);
  }

  private static OffsetDateTime getNow() {
    return OffsetDateTime.now(DEFAULT_ZONE);
  }
}
