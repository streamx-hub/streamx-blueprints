package com.streamx.blueprints.rewriter.functions;

import static org.assertj.core.api.Assertions.contentOf;

import com.streamx.blueprints.rewriter.testutils.DownloadedResource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.util.List;
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

    byte[] externalImageContent = {0, 1, 2};

    // and
    for (String externalImageUrl : externalImageUrls) {
      mockDownloadResponse(externalImageUrl, externalImageContent);
    }

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    waitForDownloadedAssets(externalImageUrls.size());
    List<DownloadedResource> expectedPublishedImages = externalImageUrls.stream()
        .map(url -> url.replace("https://", "/https_"))
        .map(url -> new DownloadedResource(url, externalImageContent))
        .toList();
    assertDownloadedAssets(expectedPublishedImages);

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
    final byte[] image1Content = {0, 1, 2};
    final byte[] image2Content = {2, 1, 0};

    // and
    mockDownloadResponses(
        "https://magento.test/url/to/image1.jpg", image1Content,
        "https://www.my-eds-server.com/relative/url/to/image2.jpg", image2Content
    );

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    waitForDownloadedAssets(2);
    assertDownloadedAssets(List.of(
        new DownloadedResource("/https_magento.test/url/to/image1.jpg", image1Content),
        new DownloadedResource("/relative/url/to/image2.jpg", image2Content)
    ));

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
    final byte[] externalResourceContent = "content".getBytes();

    // and
    mockDownloadResponse("https://www.my-eds-server.com/a/b.jpg", externalResourceContent);

    // when
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published image
    waitForDownloadedAssets(1);
    assertDownloadedAsset(0, "/a/b.jpg", externalResourceContent);

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