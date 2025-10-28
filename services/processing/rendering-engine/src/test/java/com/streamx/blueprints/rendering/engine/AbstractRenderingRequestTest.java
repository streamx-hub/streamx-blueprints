package com.streamx.blueprints.rendering.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
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
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractRenderingRequestTest extends AbstractRenderEngineTest {

  private static final String TEMPLATE_KEY = "rendering-request-test-resource-renderer";
  private static final String RENDERING_CONTEXT_EVENT_KEY =
      "rendering-request-test-resources-rendering-context";

  private InMemorySource<CloudEvent> dataSource;
  private InMemorySource<CloudEvent> renderers;
  private InMemorySource<CloudEvent> renderingRequests;
  private InMemorySource<CloudEvent> renderingContexts;
  private InMemorySink<CloudEvent> resourcesSink;

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
    String dataPublishKey = "rendering-request-test-data-type1:1";
    RenderingContext renderingContext = new RenderingContext(
        TEMPLATE_KEY,
        "rendering-request-test-data-type1:.*",
        null,
        "rendering-request-test-generated/{{id}}.html",
        null,
        outputFormat);

    dataSource.send(dataPublishEvent(dataPublishKey));
    renderingContexts.send(renderingContextPublishEvent(renderingContext));
    renderers.send(rendererPublishEvent(TEMPLATE_KEY));

    publishRenderingRequest(
        renderingContext,
        dataPublishKey
    );

    assertResourceIsProduced(
        "rendering-request-test-generated/" + dataPublishKey + ".html",
        outputEventPublishedType,
        null,
        "id = " + dataPublishKey);
  }

  @Test
  void dataPublishRenderingRequestShouldGenerateResourceAfterInitialUnpublish() {
    RenderingContext renderingContext = new RenderingContext(
        TEMPLATE_KEY,
        "rendering-request-test-data-type2:.*",
        null,
        "rendering-request-test-generated/{{id}}.html",
        null,
        OutputFormat.PAGE);
    String dataUnpublishKey = "rendering-request-test-data-type2:1";

    renderingContexts.send(renderingContextPublishEvent(renderingContext));
    renderers.send(rendererPublishEvent(TEMPLATE_KEY));
    dataSource.send(dataUnpublishEvent(dataUnpublishKey));
    await().atLeast(Duration.ofMillis(100)).untilAsserted(() ->
        assertThat(resourcesSink.received()).isEmpty()
    );

    String dataPublishKey = "rendering-request-test-data-type2:1";
    dataSource.send(dataPublishEvent(dataPublishKey));
    publishRenderingRequest(
        renderingContext,
        dataPublishKey
    );

    assertResourceIsProduced(
        "rendering-request-test-generated/" + dataPublishKey + ".html",
        outputEventPublishedType,
        null,
        "id = " + dataPublishKey);
  }

  @Test
  void dataUnpublishRenderingRequestShouldRemoveResource() {
    String dataPublishKey = "rendering-request-test-data-type3:1";
    RenderingContext renderingContext = new RenderingContext(TEMPLATE_KEY,
        "rendering-request-test-data-type3:.*",
        null,
        "rendering-request-test-generated/{{id}}.html",
        "output-template-test-{{id}}",
        outputFormat);

    dataSource.send(dataPublishEvent(dataPublishKey));
    renderingContexts.send(renderingContextPublishEvent(renderingContext));
    renderers.send(rendererPublishEvent(TEMPLATE_KEY));

    publishRenderingRequest(
        renderingContext,
        dataPublishKey
    );

    assertResourceIsProduced(
        "rendering-request-test-generated/" + dataPublishKey + ".html",
        outputEventPublishedType,
        "output-template-test-" + dataPublishKey,
        "id = " + dataPublishKey);
    resourcesSink.clear();

    dataSource.send(dataUnpublishEvent(dataPublishKey));
    unpublishRenderingRequest(
        renderingContext,
        dataPublishKey
    );

    assertResourceIsProduced(
        "rendering-request-test-generated/" + dataPublishKey + ".html",
        outputEventUnpublishedType,
        null,
        null);
  }

  protected CloudEvent renderingContextPublishEvent(RenderingContext context) {
    return renderingContextPublishEvent(RENDERING_CONTEXT_EVENT_KEY, context);
  }

  private void publishRenderingRequest(RenderingContext renderingContext, String dataKey) {
    sendRenderingRequest(renderingContext, dataKey, RenderingRequest.TYPE_PUBLISHED);
  }

  private void unpublishRenderingRequest(RenderingContext renderingContext, String dataKey) {
    sendRenderingRequest(renderingContext, dataKey, RenderingRequest.TYPE_UNPUBLISHED);
  }

  private void sendRenderingRequest(RenderingContext renderingContext, String dataKey,
      String eventType) {

    assertThat(renderingContext).isNotNull();
    String outputKeyTemplate = renderingContext.outputKeyTemplate();
    String outputTypeTemplate = renderingContext.outputTypeTemplate();

    String key = RENDERING_CONTEXT_EVENT_KEY + ":::" + dataKey;
    RenderingRequest request = new RenderingRequest(dataKey, TEMPLATE_KEY,
        outputKeyTemplate, outputTypeTemplate, outputFormat);
    renderingRequests.send(renderingRequestEvent(key, eventType, request));
  }

  private void assertResourceIsProduced(String expectedKey, String expectedEventType,
      String expectedResourceType, String expectedContent) {
    await().untilAsserted(() -> assertThat(resourcesSink.received()).hasSize(1));

    CloudEvent actual = Iterables.getOnlyElement(resourcesSink.received()).getPayload();
    assertThat(actual).isNotNull();

    WebResource resource = CloudEventUtils.getData(actual, WebResource.class);
    assertThat(resource).isNotNull();

    if (expectedContent == null) {
      assertThat(resource.getContent()).isNull();
    } else {
      assertThat(resource.getContentAsString()).isEqualTo(expectedContent);
    }
    assertThat(actual.getSubject()).isEqualTo(expectedKey);
    assertThat(resource.getType()).isEqualTo(expectedResourceType);
    assertThat(actual.getType()).isEqualTo(expectedEventType);
  }

}
