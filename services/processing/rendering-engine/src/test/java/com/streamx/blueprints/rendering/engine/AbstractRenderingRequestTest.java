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
import com.streamx.blueprints.rendering.engine.Channels.Outgoing;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractRenderingRequestTest extends AbstractRenderEngineTest {

  private static final String TEMPLATE_KEY = "rendering-request-test-resource-renderer";
  private static final String RENDERING_CONTEXT_EVENT_KEY =
      "rendering-request-test-resources-rendering-context";

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
  void initResourcesSink() {
    resourcesSink = connector.sink(outgoingChannel);
    resourcesSink.clear();
  }

  @Test
  void dataPublishRenderingRequestShouldGenerateResource() {
    String dataKey = "data-key1:1";
    RenderingContext renderingContext = newRenderingContext("data-key1:.*", null);

    dataSource.send(dataPublishEvent(dataKey));
    renderingContextsSource.send(renderingContextPublishEvent(renderingContext));
    renderersSource.send(rendererPublishEvent(TEMPLATE_KEY));
    publishRenderingRequest(renderingContext, dataKey);

    assertResourcePublishIsProduced(dataKey, null);
  }

  @Test
  void dataPublishRenderingRequestShouldGenerateResourceAfterInitialUnpublish() {
    String dataKey = "data-key2:1";
    RenderingContext renderingContext = newRenderingContext("data-key2:.*", null);

    renderingContextsSource.send(renderingContextPublishEvent(renderingContext));
    renderersSource.send(rendererPublishEvent(TEMPLATE_KEY));
    dataSource.send(dataUnpublishEvent(dataKey));
    await().atLeast(Duration.ofMillis(100)).untilAsserted(() ->
        assertThat(resourcesSink.received()).isEmpty()
    );

    dataSource.send(dataPublishEvent(dataKey));
    publishRenderingRequest(renderingContext, dataKey);

    assertResourcePublishIsProduced(dataKey, null);
  }

  @Test
  void dataUnpublishRenderingRequestShouldRemoveResource() {
    String dataKey = "data-key3:1";
    RenderingContext renderingContext = newRenderingContext(
        "data-key3:.*",
        "output-template-test-{{id}}");

    dataSource.send(dataPublishEvent(dataKey));
    renderingContextsSource.send(renderingContextPublishEvent(renderingContext));
    renderersSource.send(rendererPublishEvent(TEMPLATE_KEY));
    publishRenderingRequest(renderingContext, dataKey);

    assertResourcePublishIsProduced(dataKey, "output-template-test-" + dataKey);
    resourcesSink.clear();

    dataSource.send(dataUnpublishEvent(dataKey));
    unpublishRenderingRequest(renderingContext, dataKey);

    assertResourceUnpublishIsProduced("generated/" + dataKey + ".html");
  }

  private RenderingContext newRenderingContext(String dataKeyMatchPattern,
      String outputTypeTemplate) {
    return new RenderingContext(
        AbstractRenderingRequestTest.TEMPLATE_KEY,
        dataKeyMatchPattern,
        null,
        "generated/{{id}}.html",
        outputTypeTemplate,
        outputFormat
    );
  }

  private CloudEvent renderingContextPublishEvent(RenderingContext context) {
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
    renderingRequestsSource.send(renderingRequestEvent(key, eventType, request));
  }

  private void assertResourcePublishIsProduced(String dataKey, String expectedResourceType) {
    String expectedKey = "generated/" + dataKey + ".html";
    String expectedContent = "id = " + dataKey;

    CloudEvent actual = waitForResource();

    WebResource resource = CloudEventUtils.getData(actual, WebResource.class);
    assertThat(resource).isNotNull();

    assertThat(resource.getContentAsString()).isEqualTo(expectedContent);
    assertThat(actual.getSubject()).isEqualTo(expectedKey);
    assertThat(resource.getType()).isEqualTo(expectedResourceType);
    assertThat(actual.getType()).isEqualTo(outputEventPublishedType);
  }

  private void assertResourceUnpublishIsProduced(String expectedKey) {
    CloudEvent actual = waitForResource();

    WebResource resource = CloudEventUtils.getData(actual, WebResource.class);
    assertThat(resource).isNotNull();

    assertThat(resource.getContent()).isNull();
    assertThat(actual.getSubject()).isEqualTo(expectedKey);
    assertThat(resource.getType()).isNull();
    assertThat(actual.getType()).isEqualTo(outputEventUnpublishedType);
  }

  private CloudEvent waitForResource() {
    await().untilAsserted(() -> assertThat(resourcesSink.received()).hasSize(1));

    CloudEvent event = Iterables.getOnlyElement(resourcesSink.received()).getPayload();
    assertThat(event).isNotNull();
    return event;
  }

}
