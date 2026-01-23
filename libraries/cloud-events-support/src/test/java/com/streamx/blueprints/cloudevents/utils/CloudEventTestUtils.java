package com.streamx.blueprints.cloudevents.utils;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.jackson.JsonFormat;
import java.time.OffsetDateTime;
import java.util.Map;

public class CloudEventTestUtils {

  private static final EventFormat EVENT_FORMAT = requireNonNull(
      EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
  );

  public static void assertSameEvents(CloudEvent actualEvent, CloudEvent expectedEvent) {
    String actualEventJson = new String(EVENT_FORMAT.serialize(actualEvent));
    String expectedEventJson = new String(EVENT_FORMAT.serialize(expectedEvent));
    assertThat(actualEventJson).isEqualTo(expectedEventJson);
  }

  public static CloudEvent cloudEventWithExtensions(String subject, String eventType, Object data,
      Map<String, String> extensions) {
    OffsetDateTime now = CloudEventUtils.getNow();
    CloudEventBuilder baseBuilder = CloudEventUtils.baseBuilder(subject, eventType, now);
    CloudEventBuilder builder = CloudEventUtils.withData(baseBuilder, data);
    extensions.forEach(builder::withExtension);
    return builder.build();
  }
}

