package com.streamx.blueprints.rewriter.functions;

import static org.assertj.core.api.Assertions.contentOf;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessJsonDataFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessMagentoJsonFileWithImages() {
    // given
    final String jsonResourceKey = "default_product:62";
    final String jsonResourceContent = contentOf(
        new File("src/test/resources/magento-products.json"));

    List<String> externalImageUrls = jsonResourceContent.lines()
        .filter(line -> line.contains("http"))
        .map(line -> line
            .replaceFirst(".*http", "http")
            .replaceFirst("\\.jpg.*", ".jpg")
            .replace("\\/", "/")
        ).distinct()
        .toList();

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(externalImageUrls.size());

    List<ImmutablePair<String, String>> expectedPublishedImages = externalImageUrls.stream()
        .map(url -> new ImmutablePair<>(url.replace("https://", "/https_"), url))
        .toList();
    assertPublishedDownloadRequests(downloadRequestEvents, expectedPublishedImages);

    // and: verify published processed json
    List<CloudEvent> dataAssets = waitForEventsInSink(DATA, 1);
    String expectedPublishedContent = jsonResourceContent
        .replace("\\/", "/")
        .replace("https://", "/https_");
    assertPublishedData(dataAssets.getFirst(), jsonResourceKey, expectedPublishedContent);
  }

  @Test
  void shouldHandleRelativeUrlsInJsonFile() {
    // given
    final String jsonResourceKey = "default_product:2";
    final String jsonResourceContent = """
        {
          "productUrls": [
            "https://magento.test/url/to/image1.jpg",
            "relative/url/to/image2.jpg"
          ]
        }
        """;

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);
    assertPublishedDownloadRequest(downloadRequestEvents.get(0),
        "/https_magento.test/url/to/image1.jpg",
        "https://magento.test/url/to/image1.jpg");
    assertPublishedDownloadRequest(downloadRequestEvents.get(1),
        "/relative/url/to/image2.jpg",
        "https://www.my-eds-server.com/relative/url/to/image2.jpg");


    // and: verify published processed json
    List<CloudEvent> dataSink = waitForEventsInSink(DATA, 1);
    assertPublishedData(dataSink.getFirst(), jsonResourceKey,
        """
            {
              "productUrls": [
                "/https_magento.test/url/to/image1.jpg",
                "/relative/url/to/image2.jpg"
              ]
            }
            """);
  }

  @Test
  void shouldHandleValuesWithBothEscapedAndUnescapedChars() {
    // given
    final String jsonResourceKey = "default_product:3";
    final String jsonResourceContent = """
        {
          "productRelativeUrls": [
            "a\\/b.jpg",
            "a/b.jpg"
          ],
          "someOtherField": "Value with \\"escaped\\" quoted text"
        }
        """;

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published image
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(1);
    assertPublishedDownloadRequest(downloadRequestEvents.getFirst(),
        "/a/b.jpg",
        "https://www.my-eds-server.com/a/b.jpg");

    // and: verify published processed json
    List<CloudEvent> dataSink = waitForEventsInSink(DATA, 1);
    assertPublishedData(dataSink.getFirst(),
        jsonResourceKey,
        """
            {
              "productRelativeUrls": [
                "/a/b.jpg",
                "/a/b.jpg"
              ],
              "someOtherField": "Value with \\"escaped\\" quoted text"
            }
            """);
  }
}