package com.streamx.blueprints.rewriter.functions;

import static com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils.assertSameEvents;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

@QuarkusTest
class ProcessXmlWebResourceFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessSitemapFile() {
    // given
    final String sitemapPath = "/sitemap.xml";
    final String sitemapContent = """
        <?xml version="1.0" encoding="utf-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>https://www.my-eds-server.com/page1.html</loc>
          </url>
          <url>
            <loc>https://www.my-eds-server.com/page2.html</loc>
          </url>
        </urlset>
        """;

    // when 1
    publishWebResource(sitemapPath, sitemapContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);
    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>(
            "/page1.html",
            "https://www.my-eds-server.com/page1.html"),
        new ImmutablePair<>(
            "/page2.html",
            "https://www.my-eds-server.com/page2.html")));


    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1, 1);
    assertPublishedWebResource(webResourceEvents.getFirst(),
        "/sitemap.xml",
        """
            <?xml version="1.0" encoding="utf-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <url>
                <loc>/page1.html</loc>
              </url>
              <url>
                <loc>/page2.html</loc>
              </url>
            </urlset>
            """);

  }

  @ParameterizedTest
  @EmptySource
  @NullSource
  @CsvSource("data/products")
  void shouldRelayResourceThatHasNotMatchingPayloadType(String payloadType) {
    // given
    String path = "/products.xml";
    String content = """
        <?xml version="1.0" encoding="utf-8"?>
        <products>
          <product>
            <name>Product 1</name>
          </product>
        </products>
        """;

    // when
    CloudEvent publishEvent = publishWebResource(path, content, payloadType);

    // then
    List<CloudEvent> relayedEvents = waitForEventsInSink(payloadType, 1);

    // assert event is unchanged
    CloudEvent relayedEvent = relayedEvents.getFirst();
    assertSameEvents(relayedEvent, publishEvent);
  }

  @Test
  void shouldRelayResourceThatDoesNotHaveXmlExtension() {
    // given
    String path = "/foo.txt";
    String content = "bar";

    // when
    CloudEvent publishEvent = publishWebResource(path, content);

    // then
    List<CloudEvent> relayedEvents = waitForEventsInSink(WEB_RESOURCE, 1);

    // assert event is unchanged
    CloudEvent relayedEvent = relayedEvents.getFirst();
    assertSameEvents(relayedEvent, publishEvent);
  }
}
