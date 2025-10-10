package com.streamx.blueprints.cloudevents.utils;

import io.cloudevents.CloudEvent;
import org.assertj.core.api.Assertions;

public class CloudEventTestUtils {

  private static final CloudEventDataComparator eventComparator = new CloudEventDataComparator();

  public static void assertEventsData(CloudEvent expectedEvent, CloudEvent actualEvent) {
    Assertions.assertThat(actualEvent).usingComparator(eventComparator)
        .isEqualTo(expectedEvent);
  }
}

