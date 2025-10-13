package com.streamx.blueprints.rendering.engine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rendering.engine.Channels.Incoming;
import com.streamx.blueprints.rendering.engine.Channels.Outgoing;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractRenderingRequestTest extends AbstractRenderEngineTest {

  InMemorySource<CloudEvent> dataSource;
  InMemorySource<CloudEvent> renderers;
  InMemorySource<CloudEvent> renderingRequests;
  InMemorySource<CloudEvent> renderingContexts;
  InMemorySink<CloudEvent> resourcesSink;

  private final OutputFormat outputFormat;
  private final String outgoingChannel;
  private final String outputEventPublishedType;
  private final String outputEventUnpublishedType;

  protected AbstractRenderingRequestTest(OutputFormat outputFormat) {
    this.outputFormat = outputFormat;
    switch (outputFormat) {
      case PAGE -> {
        outgoingChannel = Outgoing.PAGES;
        outputEventPublishedType = Page.TYPE_PUBLISHED;
        outputEventUnpublishedType = Page.TYPE_UNPUBLISHED;
      }
      case FRAGMENT -> {
        outgoingChannel = Outgoing.FRAGMENTS;
        outputEventPublishedType = Fragment.TYPE_PUBLISHED;
        outputEventUnpublishedType = Fragment.TYPE_UNPUBLISHED;
      }
      default -> throw new UnsupportedOperationException("Unexpected format " + outputFormat);
    }
  }

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Incoming.DATA);
    renderers = connector.source(Incoming.RENDERERS);
    renderingRequests = connector.source(Incoming.RENDERING_REQUESTS);
    renderingContexts = connector.source(Incoming.RENDERING_CONTEXTS);

    resourcesSink = connector.sink(outgoingChannel);
    resourcesSink.clear();
  }

  @Test
  void dataPublishRenderingRequestShouldGenerateResource() {
    String templateKey = "rendering-request-test-resource-renderer";
    CloudEvent data = dataEvent("rendering-request-test-data-type1:1", Data.TYPE_PUBLISHED);
    CloudEvent renderingContextPublishEvent =
        renderingContextEvent(
            "rendering-request-test-resources-rendering-context",
            RenderingContext.TYPE_PUBLISHED,
            new RenderingContext(templateKey, "rendering-request-test-data-type1:.*",
                null,
                "rendering-request-test-generated/{{id}}.html",
                null,
                outputFormat));

    dataSource.send(data);
    renderingContexts.send(renderingContextPublishEvent);
    renderers.send(rendererPublishEvent(templateKey));

    sendRenderingRequest(
        renderingContextPublishEvent,
        data.getSubject(),
        templateKey,
        RenderingRequest.TYPE_PUBLISHED);

    await().until(() -> resourcesSink.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getSubject() + ".html",
        outputEventPublishedType,
        null,
        "id = " + data.getSubject(),
        resourcesSink.received().get(0).getPayload());
  }

  @Test
  void dataUnpublishRenderingRequestShouldRemoveResource() {
    String templateKey = "rendering-request-test-resource-renderer";
    CloudEvent data = dataEvent("rendering-request-test-data-type2:1", Data.TYPE_PUBLISHED);
    CloudEvent renderingContextPublishEvent =
        renderingContextEvent(
            "rendering-request-test-resources-rendering-context",
            RenderingContext.TYPE_PUBLISHED,
            new RenderingContext(templateKey, "rendering-request-test-data-type2:.*",
                null,
                "rendering-request-test-generated/{{id}}.html",
                "output-template-test-{{id}}",
                outputFormat));

    dataSource.send(data);
    renderingContexts.send(renderingContextPublishEvent);
    renderers.send(rendererPublishEvent(templateKey));

    sendRenderingRequest(
        renderingContextPublishEvent,
        data.getSubject(),
        templateKey,
        RenderingRequest.TYPE_PUBLISHED);

    await().until(() -> resourcesSink.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getSubject() + ".html",
        outputEventPublishedType,
        "output-template-test-" + data.getSubject(),
        "id = " + data.getSubject(),
        resourcesSink.received().get(0).getPayload());
    resourcesSink.clear();

    dataSource.send(dataEvent(data.getSubject(), Data.TYPE_UNPUBLISHED));
    sendRenderingRequest(
        renderingContextPublishEvent,
        data.getSubject(),
        templateKey,
        RenderingRequest.TYPE_UNPUBLISHED);

    await().until(() -> resourcesSink.received().size() == 1);
    assertOutput(
        "rendering-request-test-generated/" + data.getSubject() + ".html",
        outputEventUnpublishedType,
        null,
        null,
        resourcesSink.received().get(0).getPayload());
  }

  private void sendRenderingRequest(CloudEvent renderingContextPublishEvent, String dataKey,
      String templateKey, String eventType) {

    RenderingContext renderingContext =
        CloudEventUtils.getDataOrThrow(renderingContextPublishEvent, RenderingContext.class);
    String outputKeyTemplate = renderingContext.outputKeyTemplate();
    String outputTypeTemplate = renderingContext.outputTypeTemplate();

    String key = renderingContextPublishEvent.getSubject() + ":::" + dataKey;
    RenderingRequest request = new RenderingRequest(dataKey, templateKey,
        outputKeyTemplate, outputTypeTemplate, outputFormat);
    renderingRequests.send(renderingRequestEvent(key, eventType, request));
  }

  private void assertOutput(String expectedKey, String expectedEventType, String expectedType,
      String expectedContent, CloudEvent actual) {
    assertNotNull(actual);
    WebResource resource = CloudEventUtils.getDataOrThrow(actual, WebResource.class);
    if (expectedContent == null) {
      assertNull(resource.getContent());
    } else {
      assertEquals(expectedContent, resource.getContentAsString());
    }
    assertEquals(expectedKey, actual.getSubject());
    assertEquals(expectedType, resource.getType());
    assertEquals(expectedEventType, actual.getType());
  }

}
