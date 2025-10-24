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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventUtils {

  public static final String NAMESPACE_SEPARATOR = ":";

  private static final String PUBLISH_TYPE_SEARCH_TERM = ".published.";

  private static final String UNPUBLISH_TYPE_SEARCH_TERM = ".unpublished.";

  private static final URI DEFAULT_SOURCE = ConfigProvider.getConfig()
      .getOptionalValue("quarkus.application.name", String.class)
      .map(URI::create)
      .orElse(null);

  private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private CloudEventUtils() {
    // no instance
  }

  /**
   * @throws IllegalStateException when the event's data cannot be converted to the provided type
   */
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

  public static String getSubject(CloudEvent cloudEvent) {
    return Objects.requireNonNull(cloudEvent.getSubject());
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
    var builder = baseBuilder(subject, type, time);
    return withData(builder, data).build();
  }

  public static CloudEvent eventWithoutData(String subject, String type) {
    return eventWithoutData(subject, type, getNow());
  }

  public static CloudEvent eventWithoutData(String subject, String type, OffsetDateTime time) {
    return baseBuilder(subject, type, time)
        .build();
  }

  public static io.cloudevents.core.v1.CloudEventBuilder baseBuilder(String subject, String type,
      OffsetDateTime time) {
    return withIdAndSource(CloudEventBuilder.v1())
        .withSubject(subject)
        .withType(type)
        .withTime(time);
  }

  public static io.cloudevents.core.v1.CloudEventBuilder eventCopyWithData(CloudEvent event,
      Object data) {
    io.cloudevents.core.v1.CloudEventBuilder builder = withIdAndSource(CloudEventBuilder.v1(event));
    return withData(builder, data);
  }

  public static io.cloudevents.core.builder.CloudEventBuilder eventCopyWithoutData(
      CloudEvent event) {
    io.cloudevents.core.v1.CloudEventBuilder builder = withIdAndSource(CloudEventBuilder.v1(event));
    return builder.withoutData();
  }

  private static io.cloudevents.core.v1.CloudEventBuilder withIdAndSource(
      io.cloudevents.core.v1.CloudEventBuilder builder) {
    return builder
        .withId(UUID.randomUUID().toString())
        .withSource(DEFAULT_SOURCE);
  }

  public static io.cloudevents.core.v1.CloudEventBuilder withData(
      io.cloudevents.core.v1.CloudEventBuilder builder, Object data) {
    return builder
        .withDataContentType("application/json")
        .withData(PojoCloudEventData.wrap(data, objectMapper::writeValueAsBytes));
  }

  public static OffsetDateTime getNow() {
    return OffsetDateTime.now(DEFAULT_ZONE);
  }

  public static OffsetDateTime toOffsetDateTime(long utcEpochMillis) {
    Instant instant = Instant.ofEpochMilli(utcEpochMillis);
    return OffsetDateTime.ofInstant(instant, DEFAULT_ZONE);
  }
}
