package dev.streamx.blueprints.cloudevents.utils;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.lang.Nullable;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
  @SuppressWarnings("unchecked")
  public static <T> T getData(CloudEvent cloudEvent, Class<T> clazz) {
    CloudEventData cloudEventData = cloudEvent.getData();

    if (cloudEventData == null) {
      return null;
    }

    if (cloudEventData instanceof PojoCloudEventData<?> pojoData) {
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

  public static boolean isPublishingType(String type) {
    return type.contains(PUBLISH_TYPE_SEARCH_TERM);
  }

  public static boolean isUnpublishingType(String type) {
    return type.contains(UNPUBLISH_TYPE_SEARCH_TERM);
  }

  public static Optional<String> getSubjectNamespace(String subject) {
    String value = requireNonNull(subject);

    var indexOfColon = value.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfColon != -1) {
      if (indexOfColon != 0) {
        return Optional.of(value.substring(0, indexOfColon));
      }
    }
    return Optional.empty();
  }

  public static String getSubjectWithoutNamespace(String subject) {
    String value = requireNonNull(subject);

    var indexOfColon = value.indexOf(NAMESPACE_SEPARATOR);
    if (indexOfColon != -1) {
      if (indexOfColon == 0) {
        return value.substring(NAMESPACE_SEPARATOR.length());
      } else {
        return value.substring(indexOfColon + NAMESPACE_SEPARATOR.length());
      }
    }
    return subject;
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
