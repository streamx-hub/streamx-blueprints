package com.streamx.blueprints.cloudevents.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.cloudevents.CloudEvent;

public class CloudEventTestUtils {

  private static final CloudEventDataComparator eventComparator = new CloudEventDataComparator();

  public static void assertEventsData(CloudEvent expectedEvent, CloudEvent actualEvent) {
    assertThat(actualEvent).usingComparator(eventComparator)
        .isEqualTo(expectedEvent);
  }
}

