package com.streamx.blueprints.data.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.Channels.Outgoing;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractCollectorTest {

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  TestCollectorFactory testCollectorFactory;

  InMemorySource<CloudEvent> dataSource;
  InMemorySink<CloudEvent> collectedDataSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    collectedDataSink = connector.sink(Outgoing.COLLECTED_DATA);
    collectedDataSink.clear();
  }

  @Test
  void validateConnector() {
    String testKey1 = "test-key:1";
    String testKey2 = "test-key:2";
    String testType1 = "test-type:1";
    String testType2 = "test-type:2";
    dataSource.send(createDataEvent(testKey1, "type-not-matching-filter"));
    dataSource.send(createDataEvent("key-not-matching-filter", testType1));
    dataSource.send(createDataEvent("test-key:skipped", testType1));
    dataSource.send(createDataEvent(testKey1, testType1));
    dataSource.send(createDataEvent(testKey2, testType2));

    CloudEvent collectedDataEvent = waitForSingleCollectedDataEventEvent();
    Data collectedData = CloudEventUtils.getData(collectedDataEvent, Data.class);
    assertThat(collectedData).isNotNull();
    String collectedPayload = collectedData.getContentAsString();
    assertThat(collectedPayload.split(",")).containsExactly(testKey1, testKey2);
    assertThat(collectedDataEvent.getSubject()).isEqualTo(TestCollector.TEST_OUTPUT_KEY);
    assertThat(collectedData.getType()).isEqualTo(getExpectedCollectedDataOutputType());
  }

  @Test
  void validateConnectorPropertiesPassedToFactory() {
    assertThat(testCollectorFactory.getProperties()).containsExactlyInAnyOrderEntriesOf(
        Map.of("test-prop1", "test-prop1-value",
            "test-prop2", "test-prop2-value"));
  }

  protected abstract String getExpectedCollectedDataOutputType();

  private CloudEvent createDataEvent(String key, String type) {
    return CloudEventUtils.eventWithData(
        key,
        Data.TYPE_PUBLISHED,
        new Data("any", type),
        CloudEventUtils.toOffsetDateTime(1)
    );
  }

  private CloudEvent waitForSingleCollectedDataEventEvent() {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(collectedDataSink.received()).hasSize(1));

    return collectedDataSink.received().get(0).getPayload();
  }

}
