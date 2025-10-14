package com.streamx.blueprints.data.collector;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class WebResourceProducingTest {

  @Inject
  @Any
  InMemoryConnector connector;

  InMemorySource<Message<Data>> dataSource;
  InMemorySink<WebResource> webResourceSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    webResourceSink = connector.sink(Channels.Outgoing.WEB_RESOURCES);
  }

  @Test
  void shouldProduceWebResource() {
    String key = "collected:test";
    long eventTime = 1L;
    Action action = PUBLISH;
    String content = "\"some json\"";
    Message<Data> data = Message.of(new Data(content),
        Metadata.of(Key.of(key), EventTime.of(eventTime), action));

    dataSource.send(data);

    await().until(() -> !webResourceSink.received().isEmpty());
    Message<WebResource> result = webResourceSink.received().get(0);
    assertEquals(content, result.getPayload().getContentAsString());
    // configured prefix + incoming data key + json extension
    assertEquals("_data/" + key + ".json", result.getMetadata().get(Key.class).get().getValue());
    assertEquals(eventTime, result.getMetadata().get(EventTime.class).get().getValue());
    assertEquals(action, result.getMetadata().get(Action.class).get());
  }

  @Test
  void shouldNotProduceWebResourceIfKeyDoesNotMachPrefix() {
    String key = "collected:test";
    Message<Data> data = Message.of(new Data("\"some json\""),
        Metadata.of(Key.of(key), EventTime.of(1L), PUBLISH));

    dataSource.send(data.addMetadata(Key.of("not-matching-the-filter"))); // should be skipped
    dataSource.send(data);

    await().until(() -> !webResourceSink.received().isEmpty());
    Message<WebResource> result = webResourceSink.received().get(0);
    // configured prefix + incoming data key + json extension
    assertEquals("_data/" + key + ".json", result.getMetadata().get(Key.class).get().getValue());
  }

}
