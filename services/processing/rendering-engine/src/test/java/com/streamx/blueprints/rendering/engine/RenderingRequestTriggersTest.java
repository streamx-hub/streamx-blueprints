package com.streamx.blueprints.rendering.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RenderingRequestTriggersTest extends AbstractRenderEngineTest {

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
    dataSource.send(dataPublishEvent(dataKeyWillNotBeProcessed));
    renderingRequestsSink.clear();

    // test
    String dataKey = "data-type2:1";
    long dataEventTime = System.currentTimeMillis();
    dataSource.send(dataEvent(dataKey, Data.TYPE_UNPUBLISHED, dataEventTime));
    waitForRenderingRequestsInSink(0);

    dataSource.send(dataEvent(dataKey, Data.TYPE_PUBLISHED, dataEventTime));
    waitForRenderingRequestsInSink(2);
    assertPublishRequest(context1, dataKey, dataEventTime, Data.TYPE_PUBLISHED);
    assertPublishRequest(context2, dataKey, dataEventTime, Data.TYPE_PUBLISHED);
    renderingRequestsSink.clear();

    dataSource.send(dataEvent(dataKey, Data.TYPE_UNPUBLISHED, dataEventTime));
    waitForRenderingRequestsInSink(2);
    assertUnpublishRequest(context1, dataKey, dataEventTime, Data.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, dataKey, dataEventTime, Data.TYPE_UNPUBLISHED);
  }

  @Test
  void testRendererProcessing() {
    // given
    CloudEvent data1 = dataPublishEvent("data-type1:1");
    CloudEvent data2 = dataPublishEvent("data-type1:2");
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
    waitForRenderingRequestsInSink(4);
    assertUnpublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    renderingRequestsSink.clear();

    renderersSource.send(rendererEvent(rendererKey, Renderer.TYPE_PUBLISHED, rendererEventTime));
    waitForRenderingRequestsInSink(4);
    assertPublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_PUBLISHED);
    assertPublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_PUBLISHED);
    renderingRequestsSink.clear();

    renderersSource.send(rendererEvent(rendererKey, Renderer.TYPE_UNPUBLISHED, rendererEventTime));
    waitForRenderingRequestsInSink(4);
    assertUnpublishRequest(context1, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context1, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data1, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
    assertUnpublishRequest(context2, data2, rendererEventTime, Renderer.TYPE_UNPUBLISHED);
  }

  @Test
  void testRenderingContextProcessing() {
    // given
    CloudEvent data1 = dataPublishEvent("data-type3:1");
    CloudEvent data2 = dataPublishEvent("data-type3:2");
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
    waitForRenderingRequestsInSink(0);

    CloudEvent contextPublish = renderingContextEvent(
        contextKey, RenderingContext.TYPE_PUBLISHED, eventTime, context);
    renderingContextsSource.send(contextPublish);
    waitForRenderingRequestsInSink(2);
    assertPublishRequest(contextPublish, data1, eventTime, RenderingContext.TYPE_PUBLISHED);
    assertPublishRequest(contextPublish, data2, eventTime, RenderingContext.TYPE_PUBLISHED);
    renderingRequestsSink.clear();

    renderingContextsSource.send(contextUnpublish);
    waitForRenderingRequestsInSink(2);
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

    waitForRenderingRequestsInSink(1);
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

  private void waitForRenderingRequestsInSink(int expectedCount) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(renderingRequestsSink.received()).hasSize(expectedCount)
    );
  }

  private CloudEvent findRenderingRequestByKey(String key) {
    return renderingRequestsSink.received().stream()
        .map(Message::getPayload)
        .filter(event -> key.equals(event.getSubject()))
        .findFirst()
        .orElseThrow();
  }
}
