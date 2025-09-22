package dev.streamx.blueprints.externalresources.functions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.contentOf;

import dev.streamx.blueprints.data.Data;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessJsonDataFunctionTest extends BaseProcessFunctionTest {

  @InjectSpy
  ProcessJsonDataFunction processJsonDataFunction;

  @Test
  void shouldProcessMagentoJsonFileWithImages() {
    // given
    final String jsonResourceKey = "default_product:62";
    final String jsonResourceContent = contentOf(
        new File("src/test/resources/magento-products.json"), UTF_8);

    Set<String> externalImageUrls = jsonResourceContent.lines()
        .filter(line -> line.contains("http"))
        .map(line -> line
            .replaceFirst(".*http", "http")
            .replaceFirst("\\.jpg.*", ".jpg")
            .replace("\\/", "/")
        ).collect(Collectors.toSet());

    byte[] externalImageContent = new byte[]{0, 1, 2};

    // and
    for (String externalImageUrl : externalImageUrls) {
      mockDownloadResponse(externalImageUrl, externalImageContent);
    }

    // when
    overrideResourceSelectors(processJsonDataFunction,
        "$..attributes[?(@.name=='small_image' || @.name=='thumbnail')].values.*.value",
        "$..attributes[?(@.name=='small_image' || @.name=='thumbnail')].values.*.label",
        "$..primaryImage.url",
        "$..gallery.*.url"
    );

    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    waitForMessagesInSink(assetsSink, externalImageUrls.size());
    Map<String, byte[]> expectedPublishedImages = externalImageUrls.stream()
        .map(url -> url.replace("https://", "/https_"))
        .collect(Collectors.toMap(
            Function.identity(),
            e -> externalImageContent
        ));
    assertPublishedAssets(expectedPublishedImages);

    // and: verify published processed json
    waitForMessagesInSink(dataSink, 1);
    String expectedPublishedContent = jsonResourceContent
        .replace("\\/", "/")
        .replace("https://", "/https_");
    assertPublishedData(0, jsonResourceKey, expectedPublishedContent);
  }

  @Test
  void shouldHandleRelativeUrlsInJsonFile() {
    // given
    final String jsonResourceKey = "default_product:2";
    final String jsonResourceContent = """
        {
          "urls": [
            "https://magento.test/url/to/image1.jpg",
            "relative/url/to/image2.jpg"
          ]
        }
        """;
    final byte[] image1Content = new byte[]{0, 1, 2};
    final byte[] image2Content = new byte[]{2, 1, 0};

    // and
    mockDownloadResponses(
        "https://magento.test/url/to/image1.jpg", image1Content,
        "https://www.my-eds-server.com/relative/url/to/image2.jpg", image2Content
    );

    // when
    overrideResourceSelectors(processJsonDataFunction, "$.urls[*]");
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published images
    waitForMessagesInSink(assetsSink, 2);
    assertPublishedAssets(Map.of(
        "/https_magento.test/url/to/image1.jpg", image1Content,
        "/relative/url/to/image2.jpg", image2Content
    ));

    // and: verify published processed json
    waitForMessagesInSink(dataSink, 1);
    assertPublishedData(0, jsonResourceKey,
        """
            {
              "urls": [
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
          "relativeUrls": [
            "a\\/b.jpg",
            "a/b.jpg"
          ],
          "someOtherField": "Value with \\"escaped\\" quoted text"
        }
        """;
    final byte[] externalResourceContent = "content".getBytes(UTF_8);

    // and
    mockDownloadResponse("https://www.my-eds-server.com/a/b.jpg", externalResourceContent);

    // when
    overrideResourceSelectors(processJsonDataFunction, "$.relativeUrls[*]");
    publishData(jsonResourceKey, jsonResourceContent);

    // then: verify published image
    waitForMessagesInSink(assetsSink, 1);
    assertPublishedAsset(0, "/a/b.jpg", externalResourceContent);

    // and: verify published processed json
    waitForMessagesInSink(dataSink, 1);
    assertPublishedData(0,
        jsonResourceKey,
        """
            {
              "relativeUrls": [
                "/a/b.jpg",
                "/a/b.jpg"
              ],
              "someOtherField": "Value with \\"escaped\\" quoted text"
            }
            """);
  }

  private void publishData(String path, String content) {
    publish(dataChannel, new Data(content), path, "product/simple");
  }
}