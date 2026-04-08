package com.streamx.blueprints.rewriter.functions;

import static com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils.assertSameEvents;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
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
    assertPublishedWebResource(webResourceEvents.getFirst(),
        pagePath,
        "<img src='/logo.png'>");
  }

  @Test
  void shouldProcessPageWithUpdatedBaseUrlInRuntime() {
    // given
    String pagePath = "/page2.html";
    String pageContent = "<img src='./logo2.png'>";

    publishConfig("baseUrlUpdate",
        Map.of("streamx.blueprints.external-references-rewriter.base-url-for-relative-paths",
            "https://www.my-another-eds-server.com/"));

    mockDownloadResponse("https://www.my-another-eds-server.com/logo2.png", new byte[]{0, 1, 2});

    // when
    publishWebResource(pagePath, pageContent);

    // then
    waitForDownloadedAssets(1);
    assertDownloadedAsset(0,
        "/logo2.png",
        new byte[]{0, 1, 2});

    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1);
    assertPublishedWebResource(webResourceEvents.getFirst(),
        pagePath,
        "<img src='/logo2.png'>");

    //cleanup
    publishConfig("baseUrlUpdate",
        Map.of("streamx.blueprints.external-references-rewriter.base-url-for-relative-paths",
            "https://www.my-eds-server.com/"));
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
    CloudEvent relayedEvent = events.getFirst();
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
    CloudEvent relayedEvent = events.getFirst();
    assertSameEvents(relayedEvent, publishEvent);
  }
}
