package com.streamx.blueprints.json.aggregator;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import dev.streamx.quasar.reactive.messaging.utils.MetadataUtils;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import java.time.Duration;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

abstract class ProcessDataFunctionBaseTest {

  protected abstract InMemorySource<Message<Data>> getDataSource();

  protected abstract InMemorySink<Data> getDataSink();

  protected void publish(String key, String type, String payload) {
    Message<Data> message = Message.of(new Data(payload),
        Metadata.of(
            Key.of(key),
            EventTime.of(1L),
            PUBLISH,
            Properties.empty().withType(type)));
    getDataSource().send(message);
  }

  protected void publish(String key, String payload) {
    Message<Data> message = Message.of(new Data(payload),
        Metadata.of(
            Key.of(key),
            EventTime.of(1L),
            PUBLISH));
    getDataSource().send(message);
  }

  protected void unpublish(String key) {
    Message<Data> message = Message.of(null,
        Metadata.of(
            Key.of(key),
            EventTime.of(1L),
            UNPUBLISH));
    getDataSource().send(message);
  }

  protected Message<Data> findByKey(String key) {
    return getDataSink().received().stream()
        .filter(msg -> key.equals(extractKey(msg)))
        .reduce((first, second) -> second)
        .orElse(null);
  }

  protected void waitForProcessedMessages(int count) {
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
        assertThat(getDataSink().received())
            .describedAs("Current messages: " + getDataSink().received().stream()
                .map(MetadataUtils::extractKey).toList())
            .hasSize(count)
    );
  }

  protected void assertPublished(String key, String expectedType, String expectedPayload) {
    Message<Data> data = findByKey(key);
    assertThat(data).isNotNull();
    assertThat(data.getPayload()).isNotNull();
    assertThat(data.getPayload().getContentAsString()).isEqualTo(expectedPayload);
    assertThat(extractAction(data)).isSameAs(PUBLISH);
    if (expectedType == null) {
      assertThat(Properties.from(data).getType()).isEmpty();
    } else {
      assertThat(Properties.from(data).getType()).hasValue(expectedType);
    }
    assertThat(extractEventTime(data)).isOne();
  }

  protected void assertUnpublished(String key) {
    Message<Data> data = findByKey(key);
    assertThat(data).isNotNull();
    assertThat(data.getPayload()).isNull();
    assertThat(extractAction(data)).isSameAs(UNPUBLISH);
    assertThat(extractEventTime(data)).isOne();
  }

  protected void assertNoResultMessageWasSent() {
    await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(getDataSink().received()).isEmpty());
  }
}
