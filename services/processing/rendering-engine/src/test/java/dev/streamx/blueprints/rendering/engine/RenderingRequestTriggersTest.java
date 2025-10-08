package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.data.RenderingContext.OutputFormat;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RenderingRequestTriggersTest extends AbstractRenderEngineTest {

  InMemorySource<Message<Data>> dataSource;
  InMemorySource<Message<Renderer>> renderersSource;
  InMemorySource<Message<RenderingContext>> renderingContextsSource;
  InMemorySink<RenderingRequest> renderingRequestSink;

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
    Pair<String, Message<RenderingContext>> context1 = renderingContextMessage(
        "context2-1",
        PUBLISH,
        new RenderingContext(
            "renderer2-1",
            "data-type2:.*",
            null,
            "/generated2/pages/{{id}}.html",
            "data-type2-context2-1-output-type-pattern-{{id}}",
            OutputFormat.PAGE
        ));
    Pair<String, Message<RenderingContext>> context2 = renderingContextMessage(
        "context2-2",
        PUBLISH,
        new RenderingContext(
            "renderer2-2",
            "data-type2:.*",
            null,
            "/generated2/fragments/{{id}}.html",
            null,
            OutputFormat.FRAGMENT
        ));
    renderingContextsSource.send(context1.getValue());
    renderingContextsSource.send(context2.getValue());
    renderersSource.send(rendererPublishMessage("renderer2-1"));
    renderersSource.send(rendererPublishMessage("renderer2-2"));

    // DXP-1206 resolution verification
    String dataKeyWillNotBeProcessed = "data-type2:dataKeyWillNotBeProcessed";
    dataSource.send(dataMessage(dataKeyWillNotBeProcessed, PUBLISH).getValue());
    renderingRequestSink.clear();

    // test
    String dataKey = "data-type2:1";
    long dataEventTime = System.currentTimeMillis();
    dataSource.send(dataMessage(dataKey, UNPUBLISH, dataEventTime).getValue());
    assertEquals(0, renderingRequestSink.received().size());

    dataSource.send(dataMessage(dataKey, PUBLISH, dataEventTime).getValue());
    assertEquals(2, renderingRequestSink.received().size());
    assertRenderingRequest(createExpectedRequest(context1, dataKey, PUBLISH, dataEventTime));
    assertRenderingRequest(createExpectedRequest(context2, dataKey, PUBLISH, dataEventTime));
    renderingRequestSink.clear();

    dataSource.send(dataMessage(dataKey, UNPUBLISH, dataEventTime).getValue());
    assertEquals(2, renderingRequestSink.received().size());
    assertRenderingRequest(createExpectedRequest(context1, dataKey, UNPUBLISH, dataEventTime));
    assertRenderingRequest(createExpectedRequest(context2, dataKey, UNPUBLISH, dataEventTime));
  }

  @Test
  void testRendererProcessing() {
    // given
    Pair<String, Message<Data>> data1 = dataMessage("data-type1:1", PUBLISH);
    Pair<String, Message<Data>> data2 = dataMessage("data-type1:2", PUBLISH);
    dataSource.send(data1.getValue());
    dataSource.send(data2.getValue());
    Pair<String, Message<RenderingContext>> context1 = renderingContextMessage(
        "context1-1",
        PUBLISH,
        new RenderingContext(
            "renderer1-1",
            "data-type1:.*",
            null,
            "/generated1/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    Pair<String, Message<RenderingContext>> context2 = renderingContextMessage(
        "context1-2",
        PUBLISH,
        new RenderingContext(
            "renderer1-1",
            "data-type1:.*",
            null,
            "/generated1/fragments/{{id}}.html",
            "data-type1-context1-2-output-type-pattern-{{id}}",
            OutputFormat.FRAGMENT
        ));
    renderingContextsSource.send(context1.getValue());
    renderingContextsSource.send(context2.getValue());

    // test
    String rendererKey = "renderer1-1";
    long rendererEventTime = System.currentTimeMillis();
    renderersSource.send(rendererMessage(rendererKey, UNPUBLISH, rendererEventTime));
    assertEquals(4, renderingRequestSink.received().size());
    assertRenderingRequest(
        createExpectedRequest(context1, data1.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context1, data2.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data1.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data2.getKey(), UNPUBLISH, rendererEventTime));
    renderingRequestSink.clear();

    renderersSource.send(rendererMessage(rendererKey, PUBLISH, rendererEventTime));
    assertEquals(4, renderingRequestSink.received().size());
    assertRenderingRequest(
        createExpectedRequest(context1, data1.getKey(), PUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context1, data2.getKey(), PUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data1.getKey(), PUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data2.getKey(), PUBLISH, rendererEventTime));
    renderingRequestSink.clear();

    renderersSource.send(rendererMessage(rendererKey, UNPUBLISH, rendererEventTime));
    assertEquals(4, renderingRequestSink.received().size());
    assertRenderingRequest(
        createExpectedRequest(context1, data1.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context1, data2.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data1.getKey(), UNPUBLISH, rendererEventTime));
    assertRenderingRequest(
        createExpectedRequest(context2, data2.getKey(), UNPUBLISH, rendererEventTime));
  }

  @Test
  void testRenderingContextProcessing() {
    // given
    Pair<String, Message<Data>> data1 = dataMessage("data-type3:1", PUBLISH);
    Pair<String, Message<Data>> data2 = dataMessage("data-type3:2", PUBLISH);
    dataSource.send(data1.getValue());
    dataSource.send(data2.getValue());
    renderersSource.send(rendererPublishMessage("renderer3-1"));

    // test
    String contextKey = "context3-1";
    long renderingContextEventTime = System.currentTimeMillis();
    RenderingContext context = new RenderingContext(
        "renderer3-1",
        "data-type3:.*",
        null,
        "/generated3/pages/{{id}}.html",
        "data-type3-output-type-pattern-{{id}}",
        OutputFormat.PAGE
    );
    Pair<String, Message<RenderingContext>> contextUnpublish = renderingContextMessage(
        contextKey, UNPUBLISH, renderingContextEventTime, null);
    renderingContextsSource.send(contextUnpublish.getValue());
    assertEquals(0, renderingRequestSink.received().size());

    Pair<String, Message<RenderingContext>> contextPublish = renderingContextMessage(
        contextKey, PUBLISH, renderingContextEventTime, context);
    renderingContextsSource.send(contextPublish.getValue());
    assertEquals(2, renderingRequestSink.received().size());
    assertRenderingRequest(
        createExpectedRequest(contextPublish, data1.getKey(), PUBLISH, renderingContextEventTime));
    assertRenderingRequest(
        createExpectedRequest(contextPublish, data2.getKey(), PUBLISH, renderingContextEventTime));
    renderingRequestSink.clear();

    renderingContextsSource.send(contextUnpublish.getValue());
    assertEquals(2, renderingRequestSink.received().size());
    assertRenderingRequest(createExpectedRequest(contextPublish, data1.getKey(), UNPUBLISH,
        renderingContextEventTime));
    assertRenderingRequest(createExpectedRequest(contextPublish, data2.getKey(), UNPUBLISH,
        renderingContextEventTime));
  }

  @Test
  void testMatchingDataKeyAndTypeAgainstContextDataMatchPatterns() {
    // given
    Pair<String, Message<RenderingContext>> context1 = renderingContextMessage(
        "context4-1",
        PUBLISH,
        new RenderingContext(
            "renderer4-1",
            "data-type4:.*",
            "test-type/.*",
            "/generated4/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    Pair<String, Message<RenderingContext>> context2 = renderingContextMessage(
        "context4-should-be-ignored-because-none-data-match-pattern",
        PUBLISH,
        new RenderingContext(
            "renderer4-1",
            " ",
            null,
            "/generated4/pages/{{id}}.html",
            null,
            OutputFormat.PAGE
        ));
    renderingContextsSource.send(context1.getValue());
    renderingContextsSource.send(context2.getValue());
    renderersSource.send(rendererPublishMessage("renderer4-1"));

    // test
    String dataKey = "data-type4:1";
    long dataEventTime = System.currentTimeMillis();
    // should not trigger rendering request - wrong key
    dataSource.send(dataMessage("key not matching pattern", PUBLISH, dataEventTime,
        Properties.empty().withType("test-type/4")).getValue());
    // should not trigger rendering request - wrong type
    dataSource.send(dataMessage(dataKey, PUBLISH, dataEventTime,
        Properties.empty().withType("type not matching pattern")).getValue());
    // should trigger the rendering request
    dataSource.send(dataMessage(dataKey, PUBLISH, dataEventTime,
        Properties.empty().withType("test-type/4")).getValue());

    assertEquals(1, renderingRequestSink.received().size());
    assertRenderingRequest(createExpectedRequest(context1, dataKey, PUBLISH, dataEventTime));
  }

  private Pair<String, Message<RenderingRequest>> createExpectedRequest(
      Pair<String, Message<RenderingContext>> context, String dataKey, Action action,
      long eventTime) {
    String expectedRequestKey = context.getKey() + ":::" + dataKey;
    RenderingContext contextPayload = context.getValue().getPayload();
    Message<RenderingRequest> expectedRequest = Message.of(
        new RenderingRequest(
            dataKey,
            contextPayload.getRendererKey(),
            contextPayload.getOutputKeyTemplate(),
            contextPayload.getOutputTypeTemplate(),
            contextPayload.getOutputFormat()),
        Metadata.of(
            Key.of(expectedRequestKey),
            EventTime.of(eventTime),
            action
        ));
    return Pair.of(expectedRequestKey, expectedRequest);
  }

  private void assertRenderingRequest(Pair<String, Message<RenderingRequest>> request) {
    Message<RenderingRequest> expected = request.getValue();
    Message<RenderingRequest> actual = findByKey(request.getKey(), renderingRequestSink);
    assertNotNull(actual);
    assertEquals(extractKey(expected), extractKey(actual));
    assertEquals(extractEventTime(expected), extractEventTime(actual));
    assertEquals(extractAction(expected), extractAction(actual));
    assertNotNull(actual.getPayload());
    assertEquals(expected.getPayload().getDataKey(), actual.getPayload().getDataKey());
    assertEquals(expected.getPayload().getRendererKey(), actual.getPayload().getRendererKey());
    assertEquals(expected.getPayload().getOutputKeyTemplate(),
        actual.getPayload().getOutputKeyTemplate());
    assertEquals(expected.getPayload().getOutputTypeTemplate(),
        actual.getPayload().getOutputTypeTemplate());
    assertEquals(expected.getPayload().getOutputFormat(), actual.getPayload().getOutputFormat());
  }
}
