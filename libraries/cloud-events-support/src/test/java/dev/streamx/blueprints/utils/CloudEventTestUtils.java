package dev.streamx.blueprints.utils;

import io.cloudevents.CloudEvent;
import java.util.Comparator;
import org.assertj.core.api.Assertions;

public class CloudEventTestUtils {

  public static void assertEvents(CloudEvent[] expectedEvents, CloudEvent[] actualEvents) {
    Assertions.assertThat(actualEvents).usingElementComparator(new CloudEventComparator())
        .containsExactlyInAnyOrder(expectedEvents);
  }

  public static <T> CloudEvent createEvent(String subject, String type, T data) {
    if (data != null) {
      return CloudEventUtils.builderWithJsonData(data)
          .withSubject(subject)
          .withType(type).build();
    } else {
      return CloudEventUtils.builder()
          .withType(type).withSubject(subject).build();
    }
  }

}

class CloudEventComparator implements Comparator<CloudEvent> {

  @Override
  public int compare(CloudEvent e1, CloudEvent e2) {
    int subjectComparison = Comparator.nullsFirst(String::compareTo)
        .compare(e1.getSubject(), e2.getSubject());
    if (subjectComparison != 0) {
      return subjectComparison;
    }

    int typeComparison = Comparator.nullsFirst(String::compareTo)
        .compare(e1.getType(), e2.getType());
    if (typeComparison != 0) {
      return typeComparison;
    }

    byte[] data1 = e1.getData() != null ? e1.getData().toBytes() : new byte[0];
    byte[] data2 = e2.getData() != null ? e2.getData().toBytes() : new byte[0];

    // Compare lengths first for efficiency
    if (data1.length != data2.length) {
      return Integer.compare(data1.length, data2.length);
    }

    // If lengths are equal, compare content lexicographically
    for (int i = 0; i < data1.length; i++) {
      int cmp = Byte.compare(data1[i], data2[i]);
      if (cmp != 0) {
        return cmp;
      }
    }

    return 0; // Equal
  }
}