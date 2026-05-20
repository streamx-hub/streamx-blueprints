package com.streamx.blueprints.rewriter.functions;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
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

    // when
    publishWebResource(jsonResourcePath, jsonResourceContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(3);
    List<ImmutablePair<String, String>> expectedPublishedImages = List.of(
        new ImmutablePair<>("/nav-image.png_width_1200_format_pjpg_optimize_medium.png",
            "https://www.my-eds-server.com/nav-image.png?width=1200&format=pjpg&optimize=medium"),
        new ImmutablePair<>("/article-1-image.png_width_1200_format_pjpg_optimize_medium.png",
            "https://www.my-eds-server.com/article-1-image.png?width=1200&format=pjpg&optimize=medium"),
        new ImmutablePair<>("/article-2-image.png_width_1200_format_pjpg_optimize_medium.png",
            "https://www.my-eds-server.com/article-2-image.png?width=1200&format=pjpg&optimize=medium")
    );
    assertPublishedDownloadRequests(downloadRequestEvents, expectedPublishedImages);

    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 1);
    assertPublishedWebResource(webResourceEvents.getFirst(),
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
