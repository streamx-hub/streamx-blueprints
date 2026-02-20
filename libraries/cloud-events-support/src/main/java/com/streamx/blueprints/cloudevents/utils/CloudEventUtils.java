package com.streamx.blueprints.cloudevents.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.lang.Nullable;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventUtils {

  private static final String NAMESPACE_SEPARATOR = ":";

  private static final String PUBLISH_TYPE_SEARCH_TERM = ".published.";

  private static final String UNPUBLISH_TYPE_SEARCH_TERM = ".unpublished.";

  private static final Config config = ConfigProvider.getConfig();

  private static final URI DEFAULT_SOURCE = config
      .getOptionalValue("streamx.service.instance-id", String.class)
      .or(() -> config.getOptionalValue("quarkus.application.name", String.class))
      .map(URI::create)
      .orElse(null);

  private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
  private static final ObjectMapper strictObjectMapper = new ObjectMapper();
  private static final ObjectMapper tolerantObjectMapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private CloudEventUtils() {
    // no instance
  }

  /**
   * @throws IllegalStateException when the event's data cannot be converted to the provided type
   */
  @Nullable
  public static <T> T getData(CloudEvent cloudEvent, Class<T> clazz) {
    return extractData(cloudEvent, clazz, strictObjectMapper);
  }

  /**
   * @throws IllegalStateException when the event's data cannot be converted to the provided type
   */
  @Nullable
  public static <T> T getDataSkippingUnknownProperties(CloudEvent cloudEvent, Class<T> clazz) {
    return extractData(cloudEvent, clazz, tolerantObjectMapper);
  }

  @Nullable
  private static <T> T extractData(CloudEvent cloudEvent, Class<T> clazz, ObjectMapper mapper) {
    CloudEventData cloudEventData = cloudEvent.getData();
    if (cloudEventData == null) {
      return null;
    }
    if (cloudEventData instanceof JsonCloudEventData jsonData) {
      return parseJsonCloudEventData(jsonData, clazz, mapper);
    }
    throw new IllegalStateException(
        "Unexpected CloudEvent data type: " + cloudEventData.getClass().getName());
  }

  private static <T> T parseJsonCloudEventData(JsonCloudEventData jsonData, Class<T> clazz,
      ObjectMapper objectMapper) {
    try {
      return objectMapper.treeToValue(jsonData.getNode(), clazz);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Error parsing payload to " + clazz.getName(), ex);
    }
  }

  public static boolean isPublishingType(String type) {
    return type.contains(PUBLISH_TYPE_SEARCH_TERM);
  }

  public static boolean isUnpublishingType(String type) {
    return type.contains(UNPUBLISH_TYPE_SEARCH_TERM);
  }

  public static String getSubject(CloudEvent cloudEvent) {
    return Optional.ofNullable(cloudEvent.getSubject())
        .filter(subject -> !subject.isEmpty())
        .orElseThrow(NullPointerException::new);
  }

  public static Optional<String> getSubjectNamespace(String subject) {
    var indexOfSeparator = subject.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfSeparator > 0) {
      return Optional.of(subject.substring(0, indexOfSeparator));
    }
    return Optional.empty();
  }

  public static String getSubjectWithoutNamespace(CloudEvent cloudEvent) {
    String subject = getSubject(cloudEvent);
    return getSubjectWithoutNamespace(subject);
  }

  public static String getSubjectWithoutNamespace(String subject) {
    var indexOfSeparator = subject.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfSeparator == 0) {
      return subject.substring(NAMESPACE_SEPARATOR.length());
    } else if (indexOfSeparator > 0) {
      return subject.substring(indexOfSeparator + NAMESPACE_SEPARATOR.length());
    }
    return subject;
  }

  public static String createNamespacedSubject(String namespace, String rawSubject) {
    return namespace + NAMESPACE_SEPARATOR + rawSubject;
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
        .withData(JsonCloudEventData.wrap(strictObjectMapper.valueToTree(data)));
  }

  public static OffsetDateTime getNow() {
    return OffsetDateTime.now(DEFAULT_ZONE);
  }

  public static OffsetDateTime toOffsetDateTime(long utcEpochMillis) {
    Instant instant = Instant.ofEpochMilli(utcEpochMillis);
    return OffsetDateTime.ofInstant(instant, DEFAULT_ZONE);
  }
}
