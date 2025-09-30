package com.streamx.blueprints.externalresources.functions;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
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
    List<CloudEvent> assetEvents = waitForEventsInSink(EXTERNAL_ASSET, 3);
    assertPublishedAsset(assetEvents.get(0),
        "/nav-image.png_width_1200_format_pjpg_optimize_medium.png", navImageContent);
    assertPublishedAsset(assetEvents.get(1),
        "/article-1-image.png_width_1200_format_pjpg_optimize_medium.png", article1ImageContent);
    assertPublishedAsset(assetEvents.get(2),
        "/article-2-image.png_width_1200_format_pjpg_optimize_medium.png", article2ImageContent);

    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1);
    assertPublishedWebResource(webResourceEvents.get(0),
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
}
