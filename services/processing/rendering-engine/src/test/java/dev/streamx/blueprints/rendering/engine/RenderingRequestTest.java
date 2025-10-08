package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.data.RenderingContext.OutputFormat;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.rendering.engine.Channels.Incoming;
import dev.streamx.blueprints.rendering.engine.Channels.Outgoing;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RenderingRequestTest extends AbstractRenderEngineTest {

  InMemorySource<Message<Data>> dataSource;
  InMemorySource<Message<Renderer>> renderers;
  InMemorySource<Message<RenderingRequest>> renderingRequests;
  InMemorySource<Message<RenderingContext>> renderingContexts;
  InMemorySink<Resource> pages;

  @BeforeEach
  void beforeEach() {
    dataSource = this.connector.source(Incoming.DATA);
    renderers = this.connector.source(Incoming.RENDERERS);
    renderingRequests = this.connector.source(Incoming.RENDERING_REQUESTS);
    renderingContexts = this.connector.source(Incoming.RENDERING_CONTEXTS);

    pages = this.connector.sink(Outgoing.PAGES);
    pages.clear();
  }

  @Test
  void dataPublishRenderingRequestShouldGeneratePage() {
    String templateKey = "rendering-request-test-page-renderer";
    Pair<String, Message<Data>> data = dataMessage("rendering-request-test-data-type1:1", PUBLISH);
    Pair<String, Message<RenderingContext>> renderingContextPublishMessage =
        renderingContextMessage(
            "rendering-request-test-pages-rendering-context",
            PUBLISH,
            new RenderingContext(templateKey, "rendering-request-test-data-type1:.*",
                null,
                "rendering-request-test-generated/{{id}}.html",
                null,
                OutputFormat.PAGE));

    dataSource.send(data.getValue());
    renderingContexts.send(renderingContextPublishMessage.getValue());
    renderers.send(rendererPublishMessage(templateKey));

    sendRenderingRequest(
        renderingContextPublishMessage.getKey(), data.getKey(), templateKey, PUBLISH,
        renderingContextPublishMessage.getValue().getPayload().getOutputKeyTemplate(),
        renderingContextPublishMessage.getValue().getPayload().getOutputTypeTemplate());

    await().until(() -> pages.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getKey() + ".html",
        PUBLISH,
        null,
        "id = " + data.getKey(),
        pages.received().get(0));
  }

  @Test
  void dataUnpublishRenderingRequestShouldRemovePage() {
    String templateKey = "rendering-request-test-page-renderer";
    Pair<String, Message<Data>> data = dataMessage("rendering-request-test-data-type2:1", PUBLISH);
    Pair<String, Message<RenderingContext>> renderingContextPublishMessage =
        renderingContextMessage(
            "rendering-request-test-pages-rendering-context",
            PUBLISH,
            new RenderingContext(templateKey, "rendering-request-test-data-type2:.*",
                null,
                "rendering-request-test-generated/{{id}}.html",
                "output-template-test-{{id}}",
                OutputFormat.PAGE));

    dataSource.send(data.getValue());
    renderingContexts.send(renderingContextPublishMessage.getValue());
    renderers.send(rendererPublishMessage(templateKey));

    sendRenderingRequest(
        renderingContextPublishMessage.getKey(), data.getKey(), templateKey, PUBLISH,
        renderingContextPublishMessage.getValue().getPayload().getOutputKeyTemplate(),
        renderingContextPublishMessage.getValue().getPayload().getOutputTypeTemplate());

    await().until(() -> pages.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getKey() + ".html",
        PUBLISH,
        "output-template-test-" + data.getKey(),
        "id = " + data.getKey(),
        pages.received().get(0));
    pages.clear();

    dataSource.send(dataMessage(data.getKey(), UNPUBLISH).getValue());
    sendRenderingRequest(
        renderingContextPublishMessage.getKey(), data.getKey(), templateKey, UNPUBLISH,
        renderingContextPublishMessage.getValue().getPayload().getOutputKeyTemplate(),
        renderingContextPublishMessage.getValue().getPayload().getOutputTypeTemplate());

    await().until(() -> pages.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getKey() + ".html",
        UNPUBLISH,
        null,
        null,
        pages.received().get(0));
  }

  private void sendRenderingRequest(String contextKey, String dataKey, String templateKey,
      Action action,
      String outputKeyTemplate, String outputTypeTemplate) {
    String key = contextKey + ":::" + dataKey;
    RenderingRequest request = new RenderingRequest(dataKey, templateKey,
        outputKeyTemplate, outputTypeTemplate, OutputFormat.PAGE);
    this.renderingRequests.send(renderingRequestMessage(key, action, request).getValue());
  }

  private void assertOutput(String expectedKey, Action expectedAction, String expectedType,
      String expectedContent, Message<Resource> actual) {
    assertNotNull(actual);
    if (expectedContent == null) {
      assertNull(actual.getPayload());
    } else {
      assertEquals(expectedContent, actual.getPayload().getContentAsString());
    }
    assertEquals(expectedKey, extractKey(actual));
    assertEquals(expectedAction, extractAction(actual));
    assertEquals(expectedType, Properties.from(actual).getType().orElse(null));
  }

}
