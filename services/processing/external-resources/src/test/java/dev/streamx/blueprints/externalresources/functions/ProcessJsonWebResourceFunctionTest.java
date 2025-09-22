package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.WebResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessJsonWebResourceFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessEdsJsonFileWithImages() {
    // given
    final String jsonResourcePath = "/data/en-index.json";
    final String jsonResourceContent = """
        {
           "data": [
             {
               "path": "/en/nav",
               "title": "Navigation",
               "image": "/nav-image.png?width=1200&format=pjpg&optimize=medium"
             },
             {
               "path": "/en/article-1",
               "title": "Article 1",
               "image": "/article-1-image.png?width=1200&format=pjpg&optimize=medium"
             },
             {
               "path": "/en/article-2",
               "title": "Article 2",
               "image": "/article-2-image.png?width=1200&format=pjpg&optimize=medium"
             }
          ]
        }
        """;

    final byte[] navImageContent = {0, 1, 2};
    final byte[] article1ImageContent = {3, 4, 5};
    final byte[] article2ImageContent = {6, 7, 8};

    mockDownloadResponses(
        "https://www.my-eds-server.com/nav-image.png?width=1200&format=pjpg&optimize=medium",
        navImageContent,
        "https://www.my-eds-server.com/article-1-image.png?width=1200&format=pjpg&optimize=medium",
        article1ImageContent,
        "https://www.my-eds-server.com/article-2-image.png?width=1200&format=pjpg&optimize=medium",
        article2ImageContent
    );

    // when
    publishWebResource(jsonResourcePath, jsonResourceContent);

    // then
    waitForMessagesInSink(assetsSink, 3);
    assertPublishedAssets(Map.of(
        "/nav-image.png_width_1200_format_pjpg_optimize_medium.png", navImageContent,
        "/article-1-image.png_width_1200_format_pjpg_optimize_medium.png", article1ImageContent,
        "/article-2-image.png_width_1200_format_pjpg_optimize_medium.png", article2ImageContent
    ));

    waitForMessagesInSink(webResourcesSink, 1);
    assertPublishedWebResource(0,
        "/data/en-index.json",
        """
        {
           "data": [
             {
               "path": "/en/nav",
               "title": "Navigation",
               "image": "/nav-image.png_width_1200_format_pjpg_optimize_medium.png"
             },
             {
               "path": "/en/article-1",
               "title": "Article 1",
               "image": "/article-1-image.png_width_1200_format_pjpg_optimize_medium.png"
             },
             {
               "path": "/en/article-2",
               "title": "Article 2",
               "image": "/article-2-image.png_width_1200_format_pjpg_optimize_medium.png"
             }
          ]
        }
        """);
  }

  private void publishWebResource(String path, String content) {
    publish(webResourcesChannel, new WebResource(content), path, "web-resource/static");
  }
}
