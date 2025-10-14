package com.streamx.blueprints.data.collector;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.data.collector.Channels.Outgoing;
import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractCollectorTest {

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  TestCollectorFactory testCollectorFactory;

  InMemorySource<Message<Data>> dataSource;
  InMemorySink<Data> collectedDataSink;

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
    dataSource.send(getDataMessage(testKey1, "type-not-matching-filter"));
    dataSource.send(getDataMessage("key-not-matching-filter", testType1));
    dataSource.send(getDataMessage(testKey1, testType1));
    dataSource.send(getDataMessage(testKey2, "test-type:2"));

    await().until(() -> collectedDataSink.received().size() == 1);

    Message<Data> collectedData = collectedDataSink.received().get(0);
    String collectedPayload = collectedData.getPayload().getContentAsString();
    assertThat(collectedPayload.split(",")).containsExactly(testKey1, testKey2);
    assertThat(collectedData.getMetadata().get(Key.class).map(Key::getValue).orElse(null))
        .isEqualTo(TestCollector.TEST_OUTPUT_KEY);
    assertThat(Properties.from(collectedData).getType().orElse(null))
        .isEqualTo(getExpectedCollectedDataOutputType());
  }

  @Test
  void validateConnectorPropertiesPassedToFactory() {
    assertThat(testCollectorFactory.getProperties()).containsExactlyInAnyOrderEntriesOf(
        Map.of("test-prop1", "test-prop1-value",
            "test-prop2", "test-prop2-value"));
  }

  protected abstract String getExpectedCollectedDataOutputType();

  private Message<Data> getDataMessage(String key, String type) {
    return Message.of(new Data("any"),
        Metadata.of(Key.of(key), EventTime.of(1L), PUBLISH,
            Properties.empty().withType(type)));
  }

}
