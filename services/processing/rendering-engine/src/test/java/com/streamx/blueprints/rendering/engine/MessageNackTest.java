package com.streamx.blueprints.rendering.engine;

import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.toOffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.rendering.engine.Channels.Incoming;
import com.streamx.blueprints.rendering.engine.Channels.Outgoing;
import com.streamx.blueprints.rendering.engine.MessageNackTest.ResettedContext;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@QuarkusTest
@TestProfile(ResettedContext.class)
class MessageNackTest extends BaseInMemoryTest {

  public static class ResettedContext implements QuarkusTestProfile {
    // this test damages a channel by making processing throw exception, so use separate profile
    // to avoid leaks to next executed tests
  }

  private static final OffsetDateTime EVENT_TIME = toOffsetDateTime(1L);
  private InMemorySink<CloudEvent> pagesSink;

  @BeforeEach
  void initResourcesSink() {
    pagesSink = connector.sink(Outgoing.PAGES);
    pagesSink.clear();
  }

  @Test
  void shouldNackInputMessageWhenFailureInDownstreamProcessing() {
    dataSource.send(CloudEventUtils.eventWithData(
        "data-key:1",
        Data.TYPE_PUBLISHED,
        new Data("{ \"id\": \"data-key:1\" }", null),
        EVENT_TIME
    ));

    renderingContextsSource.send(CloudEventUtils.eventWithData(
        "rendering-context-key",
        RenderingContext.TYPE_PUBLISHED,
        new RenderingContext(
            "renderer-key",
            "data-key:.*",
            null,
            "generated/{{id}}.html",
            null,
            OutputFormat.PAGE
        ),
        EVENT_TIME
    ));

    try (MockedStatic<CloudEventUtils> cloudEventUtils = mockStatic(CloudEventUtils.class,
        CALLS_REAL_METHODS)) {
      // publishing renderer should trigger producing a rendering request. Make that fail:
      cloudEventUtils.when(() -> CloudEventUtils.eventWithData(
          eq("rendering-context-key:::data-key:1"),
          eq(Renderer.TYPE_PUBLISHED),
          any(RenderingRequest.class),
          any(OffsetDateTime.class)
      )).thenThrow(new RuntimeException("Dummy exception"));

      AtomicBoolean nackReceived = new AtomicBoolean(false);
      Message<CloudEvent> rendererMessage = Message
          .of(CloudEventUtils.eventWithData(
              "renderer-key",
              Renderer.TYPE_PUBLISHED,
              new Renderer("id = {{id}}", "renderers/simple"),
              EVENT_TIME
          ))
          .withNack(throwable -> {
            nackReceived.set(true);
            return CompletableFuture.completedFuture(null);
          });

      // publish renderer message and expect it be NACKed
      connector.source(Incoming.RENDERERS).send(rendererMessage);

      await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
          assertThat(nackReceived).isTrue()
      );

      assertNoPageProduced();
    }
  }

  private void assertNoPageProduced() {
    await()
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(pagesSink.received()).isEmpty());
  }

}
