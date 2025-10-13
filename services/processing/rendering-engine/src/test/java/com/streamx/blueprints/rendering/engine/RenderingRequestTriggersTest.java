package com.streamx.blueprints.rendering.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RenderingRequestTriggersTest extends AbstractRenderEngineTest {

  private InMemorySource<CloudEvent> dataSource;
  private InMemorySource<CloudEvent> renderersSource;
  private InMemorySource<CloudEvent> renderingContextsSource;
  private InMemorySink<CloudEvent> renderingRequestSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    renderersSource = connector.source(Channels.Incoming.RENDERERS);
    renderingContextsSource = connector.source(Channels.Incoming.RENDERING_CONTEXTS);
    renderingRequestSink = connector.sink(Channels.Outgoing.RENDERING_REQUESTS);
    renderingRequestSink.clear();
  }

  @Test
  void testDataProcessing() {
    // given
    CloudEvent context1 = renderingContextPublishEvent(
        "context2-1",
        new RenderingContext(
            "renderer2-1",
            "data-type2:.*",
            null,
            "/generated2/pages/{{id}}.html",
            "data-type2-context2-1-output-type-pattern-{{id}}",
            OutputFormat.PAGE
        ));
    CloudEvent context2 = renderingContextPublishEvent(
        "context2-2",
        new RenderingContext(
            "renderer2-2",
            "data-type2:.*",
            null,
            "/generated2/fragments/{{id}}.html",
            null,
            OutputFormat.FRAGMENT
        ));
    renderingContextsSource.send(context1);
    renderingContextsSource.send(context2);
    renderersSource.send(rendererPublishEvent("renderer2-1"));
    renderersSource.send(rendererPublishEvent("renderer2-2"));

    // DXP-1206 resolution verification
    String dataKeyWillNotBeProcessed = "data-type2:dataKeyWillNotBeProcessed";
    dataSource.send(dataEvent(dataKeyWillNotBeProcessed, Data.TYPE_PUBLISHED));
    renderingRequestSink.clear();

    // test
    String dataKey = "data-type2:1";
    long dataEventTime = System.currentTimeMillis();
    dataSource.send(dataEvent(dataKey, Data.TYPE_UNPUBLISHED, dataEventTime));
    assertThat(renderingRequestSink.received()).isEmpty();

    dataSource.send(dataEvent(dataKey, Data.TYPE_PUBLISHED, dataEventTime));
    assertThat(renderingRequestSink.received()).hasSize(2);
    assertPublishRequest(context1, dataKey, dataEventTime, Data.TYPE_PUBLISHED);
    assertPublishRequest(context2, dataKey, dataEventTime, Data.TYPE_PUBLISHED);
    renderingRequestSink.clear();

    dataSource.send(dataEvent(dataKey, Data.TYPE_UNPUBLISHED, dataEventTime));
    assertThat(renderingRequestSink.received()).hasSize(2);
    assertUnpublishRequest(context1, dataKey, dataEventTime, Data.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, dataKey, dataEventTime, Data.TYPE_UNPUBLISHED);
  }

  @Test
  void testRendererProcessing() {
    // given
    CloudEvent data1 = dataEvent("data-type1:1", Data.TYPE_PUBLISHED);
    CloudEvent data2 = dataEvent("data-type1:2", Data.TYPE_PUBLISHED);
    dataSource.send(data1);
    dataSource.send(data2);
    CloudEvent context1 = renderingContextPublishEvent(
        "context1-1",
        new RenderingContext(
            "renderer1-1",
            "data-type1:.*",
            null,
            "/generated1/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    CloudEvent context2 = renderingContextPublishEvent(
        "context1-2",
        new RenderingContext(
            "renderer1-1",
            "data-type1:.*",
            null,
            "/generated1/fragments/{{id}}.html",
            "data-type1-context1-2-output-type-pattern-{{id}}",
            OutputFormat.FRAGMENT
        ));
    renderingContextsSource.send(context1);
    renderingContextsSource.send(context2);

    // test
    String rendererKey = "renderer1-1";
    long rendererEventTime = System.currentTimeMillis();
    renderersSource.send(rendererEvent(rendererKey, Renderer.TYPE_UNPUBLISHED, rendererEventTime));
    assertThat(renderingRequestSink.received()).hasSize(4);
    assertUnpublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    renderingRequestSink.clear();

    renderersSource.send(rendererEvent(rendererKey, Renderer.TYPE_PUBLISHED, rendererEventTime));
    assertThat(renderingRequestSink.received()).hasSize(4);
    assertPublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_PUBLISHED);
    renderingRequestSink.clear();

    renderersSource.send(rendererEvent(rendererKey, Renderer.TYPE_UNPUBLISHED, rendererEventTime));
    assertThat(renderingRequestSink.received()).hasSize(4);
    assertUnpublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
  }

  @Test
  void testRenderingContextProcessing() {
    // given
    CloudEvent data1 = dataEvent("data-type3:1", Data.TYPE_PUBLISHED);
    CloudEvent data2 = dataEvent("data-type3:2", Data.TYPE_PUBLISHED);
    dataSource.send(data1);
    dataSource.send(data2);
    renderersSource.send(rendererPublishEvent("renderer3-1"));

    // test
    String contextKey = "context3-1";
    long eventTime = System.currentTimeMillis();
    RenderingContext context = new RenderingContext(
        "renderer3-1",
        "data-type3:.*",
        null,
        "/generated3/pages/{{id}}.html",
        "data-type3-output-type-pattern-{{id}}",
        OutputFormat.PAGE
    );
    CloudEvent contextUnpublish = renderingContextEvent(
        contextKey, RenderingContext.TYPE_UNPUBLISHED, eventTime, null);
    renderingContextsSource.send(contextUnpublish);
    assertThat(renderingRequestSink.received()).isEmpty();

    CloudEvent contextPublish = renderingContextEvent(
        contextKey, RenderingContext.TYPE_PUBLISHED, eventTime, context);
    renderingContextsSource.send(contextPublish);
    assertThat(renderingRequestSink.received()).hasSize(2);
    assertPublishRequest(contextPublish, data1, eventTime, RenderingContext.TYPE_PUBLISHED);
    assertPublishRequest(contextPublish, data2, eventTime, RenderingContext.TYPE_PUBLISHED);
    renderingRequestSink.clear();

    renderingContextsSource.send(contextUnpublish);
    assertThat(renderingRequestSink.received()).hasSize(2);
    assertUnpublishRequest(contextPublish, data1, eventTime, RenderingContext.TYPE_UNPUBLISHED);
    assertUnpublishRequest(contextPublish, data2, eventTime, RenderingContext.TYPE_UNPUBLISHED);
  }

  @Test
  void testMatchingDataKeyAndTypeAgainstContextDataMatchPatterns() {
    // given
    CloudEvent context1 = renderingContextPublishEvent(
        "context4-1",
        new RenderingContext(
            "renderer4-1",
            "data-type4:.*",
            "test-type/.*",
            "/generated4/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    CloudEvent context2 = renderingContextPublishEvent(
        "context4-should-be-ignored-because-none-data-match-pattern",
        new RenderingContext(
            "renderer4-1",
            " ",
            null,
            "/generated4/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    renderingContextsSource.send(context1);
    renderingContextsSource.send(context2);
    renderersSource.send(rendererPublishEvent("renderer4-1"));

    // test
    String dataKey = "data-type4:1";
    long dataEventTime = System.currentTimeMillis();
    // should not trigger rendering request - wrong key
    dataSource.send(dataEvent("key not matching pattern", Data.TYPE_PUBLISHED, dataEventTime,
        "test-type/4"));
    // should not trigger rendering request - wrong type
    dataSource.send(dataEvent(dataKey, Data.TYPE_PUBLISHED, dataEventTime,
        "type not matching pattern"));
    // should trigger the rendering request
    dataSource.send(dataEvent(dataKey, Data.TYPE_PUBLISHED, dataEventTime,
        "test-type/4"));

    assertThat(renderingRequestSink.received()).hasSize(1);
    assertPublishRequest(context1, dataKey, dataEventTime, Data.TYPE_PUBLISHED);
  }

  private CloudEvent createPublishRequest(CloudEvent context, String dataKey, long eventTime) {
    return createRequest(context, dataKey, RenderingRequest.TYPE_PUBLISHED, eventTime);
  }

  private CloudEvent createUnpublishRequest(CloudEvent context, String dataKey, long eventTime) {
    return createRequest(context, dataKey, RenderingRequest.TYPE_UNPUBLISHED, eventTime);
  }

  private CloudEvent createRequest(CloudEvent contextEvent, String dataKey, String eventType,
      long eventTime) {
    String expectedRequestKey = contextEvent.getSubject() + ":::" + dataKey;
    RenderingContext context = CloudEventUtils.getData(contextEvent, RenderingContext.class);
    assertThat(context).isNotNull();
    return CloudEventUtils.eventWithData(
        expectedRequestKey,
        eventType,
        new RenderingRequest(
            dataKey,
            context.rendererKey(),
            context.outputKeyTemplate(),
            context.outputTypeTemplate(),
            context.outputFormat()),
        CloudEventUtils.toOffsetDateTime(eventTime));
  }

  private void assertPublishRequest(CloudEvent context, CloudEvent sourceEvent, long eventTime,
      String expectedRequestEventType) {
    assertPublishRequest(context, sourceEvent.getSubject(), eventTime, expectedRequestEventType);
  }

  private void assertPublishRequest(CloudEvent context, String dataKey, long eventTime,
      String expectedRequestEventType) {
    CloudEvent expectedRequestEvent = createPublishRequest(context, dataKey, eventTime);
    assertRenderingRequest(expectedRequestEvent, expectedRequestEventType);
  }

  private void assertUnpublishRequest(CloudEvent context, CloudEvent sourceEvent, long eventTime,
      String expectedRequestEventType) {
    assertUnpublishRequest(context, sourceEvent.getSubject(), eventTime, expectedRequestEventType);
  }

  private void assertUnpublishRequest(CloudEvent context, String dataKey, long eventTime,
      String expectedRequestEventType) {
    CloudEvent expectedRequestEvent = createUnpublishRequest(context, dataKey, eventTime);
    assertRenderingRequest(expectedRequestEvent, expectedRequestEventType);
  }

  private void assertRenderingRequest(CloudEvent expectedEvent, String expectedRequestEventType) {
    CloudEvent actualEvent = findRenderingRequestByKey(expectedEvent.getSubject());
    assertThat(actualEvent.getSubject()).isEqualTo(expectedEvent.getSubject());
    assertThat(actualEvent.getTime()).isEqualTo(expectedEvent.getTime());
    assertThat(actualEvent.getType()).isEqualTo(expectedRequestEventType);

    var actualRequest = CloudEventUtils.getData(actualEvent, RenderingRequest.class);
    assertThat(actualRequest).isNotNull();

    var expectedRequest = CloudEventUtils.getData(expectedEvent, RenderingRequest.class);
    assertThat(expectedRequest).isNotNull();

    assertThat(actualRequest.dataKey()).isEqualTo(expectedRequest.dataKey());
    assertThat(actualRequest.rendererKey()).isEqualTo(expectedRequest.rendererKey());
    assertThat(actualRequest.outputKeyTemplate()).isEqualTo(expectedRequest.outputKeyTemplate());
    assertThat(actualRequest.outputTypeTemplate()).isEqualTo(expectedRequest.outputTypeTemplate());
    assertThat(actualRequest.outputFormat()).isSameAs(expectedRequest.outputFormat());
  }

  private CloudEvent findRenderingRequestByKey(String key) {
    return renderingRequestSink.received().stream()
        .map(Message::getPayload)
        .filter(event -> key.equals(event.getSubject()))
        .findFirst()
        .orElseThrow();
  }
}
