package com.streamx.blueprints.rendering.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(BaseQuarkusIntegrationTestProfile.class)
public class RenderingEngineIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldRenderPage() {
    // when: publish product
    String productType = "product/simple";
    String productJson = """
        {
          "id": "1",
          "name": "Bag"
        }
        """;
    sendEvent(
        "product:1",
        Data.TYPE_PUBLISHED,
        new Data(productJson, productType),
        Channels.Incoming.DATA
    );

    // and: publish RenderingContext
    String rendererKey = "test-renderer";
    RenderingContext renderingContext = new RenderingContext(
        rendererKey,
        "product:.*",
        productType,
        "product-pages/{{id}}.html",
        "template-{{id}}",
        OutputFormat.PAGE
    );

    String renderingContextKey = "test-rendering-context";
    sendEvent(
        renderingContextKey,
        RenderingContext.TYPE_PUBLISHED,
        renderingContext,
        Channels.Incoming.RENDERING_CONTEXTS
    );

    // and: publish Renderer
    Renderer renderer = new Renderer("<html>Product with ID {{id}} and name {{name}}</html>");
    sendEvent(
        rendererKey,
        Renderer.TYPE_PUBLISHED,
        renderer,
        Channels.Incoming.RENDERERS
    );

    // then: expect a rendering request to be produced by the service and relayed to input channel
    sendFromOutgoingToIncomingChannel(
        Channels.Outgoing.RENDERING_REQUESTS, Channels.Incoming.RENDERING_REQUESTS);

    // then: expect a rendered page
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.Outgoing.PAGES);
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("rendering-engine");
    assertThat(outgoingEvent.getSubject()).isEqualTo("product-pages/1.html");
    assertThat(outgoingEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);

    Page outgoingPage = CloudEventUtils.getData(outgoingEvent, Page.class);
    assertThat(outgoingPage).isNotNull();
    assertThat(outgoingPage.getType()).isEqualTo("template-1");
    assertThat(outgoingPage.getContentAsString())
        .isEqualTo("<html>Product with ID 1 and name Bag</html>");
  }
}