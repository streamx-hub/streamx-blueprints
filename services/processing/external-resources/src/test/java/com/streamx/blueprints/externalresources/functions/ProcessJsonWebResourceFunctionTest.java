package com.streamx.blueprints.externalresources.functions;

import com.streamx.blueprints.externalresources.testutils.DownloadedResource;
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
    waitForDownloadedAssets(3);
    assertDownloadedAssets(List.of(
        new DownloadedResource("/nav-image.png_width_1200_format_pjpg_optimize_medium.png",
            navImageContent),
        new DownloadedResource("/article-1-image.png_width_1200_format_pjpg_optimize_medium.png",
            article1ImageContent),
        new DownloadedResource("/article-2-image.png_width_1200_format_pjpg_optimize_medium.png",
            article2ImageContent)
    ));

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
