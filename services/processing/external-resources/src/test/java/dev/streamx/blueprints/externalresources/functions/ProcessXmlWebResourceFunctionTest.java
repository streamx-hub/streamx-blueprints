package dev.streamx.blueprints.externalresources.functions;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.streamx.blueprints.data.WebResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Message;
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
    waitForMessagesInSink(pagesSink, 2);
    assertPublishedPage(0,
        "/page1.html",
        page1Content);

    assertPublishedPage(1,
        "/page2.html",
        page2Content);

    waitForMessagesInSink(webResourcesSink, 1);
    assertPublishedWebResource(0,
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

    // when 2: simulate the result messages are grabbed by second instance of the service
    sendMessagesFromSinkToChannel(pagesSink, pagesChannel);

    // then
    waitForMessagesInSink(pagesSink, 4);
    waitForMessagesInSink(assetsSink, 4);

    // page1 contains links to image1 and image2
    assertPublishedAsset(0,
        "/image1.jpg",
        image1Content);

    assertPublishedAsset(1,
        "/image2.jpg",
        image2Content);

    assertPublishedPage(2,
        "/page1.html",
        """
            Page 1.
            <img src="/image1.jpg">
            <img src="/image2.jpg">
            """);

    // page2 contains links to image3 and image4
    assertPublishedAsset(2,
        "/image3.jpg",
        image3Content);

    assertPublishedAsset(3,
        "/image4.jpg",
        image4Content);

    assertPublishedPage(3,
        "/page2.html",
        """
            Page 2.
            <img src="/image3.jpg">
            <img src="/image4.jpg">
            """);

    // and: expect the sitemap file message to not processed by the second instance
    waitForMessagesInSink(webResourcesSink, 1);
  }

  @ParameterizedTest
  @EmptySource
  @NullSource
  @CsvSource("data/products")
  void shouldRelayResourceThatHasNotMatchingSxType(String sxType) {
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
    Message<WebResource> publishMessage = publish(webResourcesChannel, new WebResource(content),
        path, sxType);

    // then
    waitForMessagesInSink(webResourcesSink, 1);

    // assert message is unchanged
    Message<WebResource> relayedMessage = webResourcesSink.received().get(0);
    assertSameMessages(relayedMessage, publishMessage);
  }

  @Test
  void shouldRelayResourceThatDoesNotHaveXmlExtension() {
    // given
    String path = "/foo.txt";
    String content = "bar";

    // when
    Message<WebResource> publishMessage = publishWebResource(path, content);

    // then
    waitForMessagesInSink(webResourcesSink, 1);

    // assert message is unchanged
    Message<WebResource> relayedMessage = webResourcesSink.received().get(0);
    assertSameMessages(relayedMessage, publishMessage);
  }

  private Message<WebResource> publishWebResource(String path, String content) {
    return publish(webResourcesChannel, new WebResource(content), path, "web-resource/static");
  }
}
