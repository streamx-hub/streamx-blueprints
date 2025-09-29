package com.streamx.blueprints.externalresources.functions;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

@QuarkusTest
class ProcessHtmlWebResourceFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessPage() {
    // given
    String pagePath = "/page1.html";
    String pageContent = "<img src='./logo.png'>";

    mockDownloadResponse("https://www.my-eds-server.com/logo.png", new byte[]{0, 1, 2});

    // when
    publishWebResource(pagePath, pageContent);

    // then
    waitForDownloadedAssets(1);
    assertDownloadedAsset(0,
        "/logo.png",
        new byte[]{0, 1, 2});

    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1);
    assertPublishedWebResource(webResourceEvents.get(0),
        pagePath,
        "<img src='/logo.png'>");
  }

  @ParameterizedTest
  @EmptySource
  @NullSource
  @CsvSource("data/products")
  void shouldRelayResourceThatHasNotMatchingPayloadType(String payloadType) {
    // given
    String path = "page.html";
    String content = "Hello World";

    // when
    CloudEvent publishEvent = publishWebResource(path, content, payloadType);

    // then
    List<CloudEvent> events = waitForEventsInSink(payloadType, 1);

    // assert event is unchanged
    CloudEvent relayedEvent = events.get(0);
    assertSameEvents(relayedEvent, publishEvent);
  }

  @Test
  void shouldRelayResourceThatDoesNotHaveHtmlExtension() {
    // given
    String path = "/foo.txt";
    String content = "bar";

    // when
    CloudEvent publishEvent = publishWebResource(path, content);

    // then
    List<CloudEvent> events = waitForEventsInSink(WEB_RESOURCE, 1);

    // assert event is unchanged
    CloudEvent relayedEvent = events.get(0);
    assertSameEvents(relayedEvent, publishEvent);
  }
}
