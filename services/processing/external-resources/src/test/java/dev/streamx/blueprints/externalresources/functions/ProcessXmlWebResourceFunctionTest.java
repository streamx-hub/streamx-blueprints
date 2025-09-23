package dev.streamx.blueprints.externalresources.functions;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;

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

    final String page1Content = """
        Page 1.
        <img src="image1.jpg">
        <img src="image2.jpg">
        """;
    final String page2Content = """
        Page 2.
        <img src="image3.jpg">
        <img src="image4.jpg">
        """;
    final byte[] image1Content = "Image 1".getBytes(UTF_8);
    final byte[] image2Content = "Image 2".getBytes(UTF_8);
    final byte[] image3Content = "Image 3".getBytes(UTF_8);
    final byte[] image4Content = "Image 4".getBytes(UTF_8);

    final Map<String, String> externalPages = Map.of(
        "page1.html", page1Content,
        "page2.html", page2Content
    );

    final Map<String, byte[]> externalImages = Map.of(
        "image1.jpg", image1Content,
        "image2.jpg", image2Content,
        "image3.jpg", image3Content,
        "image4.jpg", image4Content
    );

    for (var page : externalPages.entrySet()) {
      mockDownloadResponse("https://www.my-eds-server.com/" + page.getKey(), page.getValue());
    }
    for (var image : externalImages.entrySet()) {
      mockDownloadResponse("https://www.my-eds-server.com/" + image.getKey(), image.getValue());
    }

    // when 1
    publishWebResource(sitemapPath, sitemapContent);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(EXTERNAL_PAGE, 2);
    assertPublishedPage(pageEvents.get(0),
        "/page1.html",
        page1Content);

    assertPublishedPage(pageEvents.get(1),
        "/page2.html",
        page2Content);

    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1);
    assertPublishedWebResource(webResourceEvents.get(0),
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

    // when 2: simulate the result events are grabbed by second instance of the service
    pageEvents.forEach(resourcesChannel::send);

    // then
    pageEvents = waitForEventsInSink(EXTERNAL_PAGE, 4);
    List<CloudEvent> assetEvents = waitForEventsInSink(EXTERNAL_ASSET, 4);

    // page1 contains links to image1 and image2
    assertPublishedAsset(assetEvents.get(0),
        "/image1.jpg",
        image1Content);

    assertPublishedAsset(assetEvents.get(1),
        "/image2.jpg",
        image2Content);

    assertPublishedPage(pageEvents.get(2),
        "/page1.html",
        """
            Page 1.
            <img src="/image1.jpg">
            <img src="/image2.jpg">
            """);

    // page2 contains links to image3 and image4
    assertPublishedAsset(assetEvents.get(2),
        "/image3.jpg",
        image3Content);

    assertPublishedAsset(assetEvents.get(3),
        "/image4.jpg",
        image4Content);

    assertPublishedPage(pageEvents.get(3),
        "/page2.html",
        """
            Page 2.
            <img src="/image3.jpg">
            <img src="/image4.jpg">
            """);

    // and: expect the sitemap file events to be not processed by the second instance
    waitForEventsInSink(WEB_RESOURCE, 1);
  }

  @ParameterizedTest
  @EmptySource
  @CsvSource("data/products")
  void shouldRelayResourceThatHasNotMatchingEventType(String eventType) {
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
    CloudEvent publishEvent = publishWebResource(path, content, eventType);

    // then
    List<CloudEvent> relayedEvents = waitForEventsInSink(eventType, 1);

    // assert event is unchanged
    CloudEvent relayedEvent = relayedEvents.get(0);
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
    CloudEvent relayedEvent = relayedEvents.get(0);
    assertSameEvents(relayedEvent, publishEvent);
  }
}
