package com.streamx.blueprints.composition;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(BaseQuarkusIntegrationTestProfile.class)
public class CompositionEngineIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldComposePage() {
    // when: publish layout
    String layoutKey = "/user-page-layout";
    String layoutContent = """
        <html>
          Hello {{#insert name="name.html"}}.
        </html>
        """;
    Layout layout = new Layout(layoutContent, "page-layouts");
    sendEvent(layoutKey, Layout.TYPE_PUBLISHED, layout, Channels.INCOMING_LAYOUTS);

    // and: publish composition
    String compositionKey = "/john-page.html";
    String compositionContent = """
        {{#define name="name.html"}}
        John
        """;

    Composition composition = new Composition(compositionContent, null, layoutKey);
    sendEvent(compositionKey, Composition.TYPE_PUBLISHED, composition,
        Channels.INCOMING_COMPOSITIONS);

    // then: expect page compose request to be produced by the service and relayed to input channel
    sendFromOutgoingToIncomingChannel(
        Channels.OUTGOING_PAGE_COMPOSE_REQUESTS, Channels.INCOMING_PAGE_COMPOSE_REQUESTS);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.OUTGOING_PAGES);
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("composition-engine");
    assertThat(outgoingEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getSubject()).isEqualTo(compositionKey);

    Page outgoingPage = CloudEventUtils.getData(outgoingEvent, Page.class);
    assertThat(outgoingPage).isNotNull();
    assertThat(outgoingPage.getType()).isEqualTo(layout.getType());
    assertThat(outgoingPage.getContentAsString()).isEqualTo("""
        <html>
          Hello John.
        </html>
        """);
  }

}