package com.streamx.blueprints.json.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import java.time.Duration;
import org.eclipse.microprofile.reactive.messaging.Message;

abstract class ProcessDataFunctionBaseTest {

  protected abstract StatefulInMemorySource getDataSource();

  protected abstract InMemorySink<CloudEvent> getDataSink();

  protected void publish(String key, String type, String payload) {
    CloudEvent event = CloudEventUtils.eventWithData(
        key,
        Data.TYPE_PUBLISHED,
        new Data(payload, type),
        CloudEventUtils.toOffsetDateTime(1L)
    );
    getDataSource().send(event);
  }

  protected void publish(String key, String payload) {
    publish(key, null, payload);
  }

  protected void unpublish(String key) {
    CloudEvent event = CloudEventUtils.eventWithoutData(
        key,
        Data.TYPE_UNPUBLISHED,
        CloudEventUtils.toOffsetDateTime(1L)
    );
    getDataSource().send(event);
  }

  protected CloudEvent findByKey(String key) {
    return getDataSink().received().stream()
        .map(Message::getPayload)
        .filter(msg -> key.equals(msg.getSubject()))
        .reduce((first, second) -> second)
        .orElse(null);
  }

  protected void waitForProcessedMessages(int count) {
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
        assertThat(getDataSink().received())
            .describedAs("Current messages: " + getDataSink().received().stream()
                .map(Message::getPayload)
                .map(CloudEvent::getSubject).toList())
            .hasSize(count)
    );
  }

  protected void assertPublished(String key, String expectedType, String expectedPayload) {
    CloudEvent event = findByKey(key);
    assertThat(event).isNotNull();
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNotNull();
    assertThat(data.getContentAsString()).isEqualTo(expectedPayload);
    assertThat(event.getType()).isEqualTo(Data.TYPE_PUBLISHED);
    assertThat(data.getType()).isEqualTo(expectedType);
    assertThat(event.getTime()).isEqualTo(CloudEventUtils.toOffsetDateTime(1));
  }

  protected void assertUnpublished(String key) {
    CloudEvent event = findByKey(key);
    assertThat(event).isNotNull();
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNull();
    assertThat(event.getType()).isSameAs(Data.TYPE_UNPUBLISHED);
    assertThat(event.getTime()).isEqualTo(CloudEventUtils.toOffsetDateTime(1));
  }

  protected void assertNoResultMessageWasSent() {
    await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(getDataSink().received()).isEmpty());
  }
}
