package com.streamx.blueprints.rewriter.functions;

import static com.streamx.blueprints.cloudevents.utils.CloudEventTestUtils.assertSameEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessPageFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessPageWithSingleInternalLink() {
    // given
    final String pagePath = "page.html";
    final String pageContent = "⭐<img src='image-🚀.jpg'>⭐";

    // when
    publishPageWithExtension(pagePath, pageContent,
        Map.of("indexable", "true", "category", "blogs"));

    // then: wait for expected events to be published
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(1);
    assertPublishedDownloadRequest(downloadRequestEvents.get(0),
        "/image-_.jpg",
        "https://www.my-eds-server.com/image-🚀.jpg");

    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    CloudEvent editedPageEvent = pageEvents.getFirst();

    // and: assert content of published events
    assertPublishedPage(editedPageEvent,
        "/page.html",
        "⭐<img src='/image-_.jpg'>⭐");

    // and: assert extensions are rewritten to outgoing edited page event
    assertThat(editedPageEvent.getExtension("indexable")).isEqualTo("true");
    assertThat(editedPageEvent.getExtension("category")).isEqualTo("blogs");
  }

  @Test
  void shouldProcessPageWithInternalLinksLeadingToSameImage() {
    // given
    final String pagePath = "page1.html";
    final String pageContent = """
        <img src='https://www.my-eds-server.com/image.png'>
        <img src='/image.png'>
        <img src='image.png'>
        <img src='./image.png'>
        <img src='./image.png?a=b&c=d'>
        <img src='image.png?a=b&c=d'>
        <img src='/image.png?a=b&c=d'>
        <img src='https://www.my-eds-server.com/image.png?a=b&c=d'>
        """;

    // when
    publishPage(pagePath, pageContent);

    // then: wait for expected events to be published
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);

    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertPublishedDownloadRequest(downloadRequestEvents.get(0),
        "/image.png",
        "https://www.my-eds-server.com/image.png");
    assertPublishedDownloadRequest(downloadRequestEvents.get(1),
        "/image.png_a_b_c_d.png",
        "https://www.my-eds-server.com/image.png?a=b&c=d");

    assertPublishedPage(pageEvents.getFirst(),
        "/page1.html",
        """
            <img src='/image.png'>
            <img src='/image.png'>
            <img src='/image.png'>
            <img src='/image.png'>
            <img src='/image.png_a_b_c_d.png'>
            <img src='/image.png_a_b_c_d.png'>
            <img src='/image.png_a_b_c_d.png'>
            <img src='/image.png_a_b_c_d.png'>
            """);
  }

  @Test
  void shouldProcessPageWithPictureSrcset() {
    // given
    final String page1Path = "page1.html";
    final String page1Content = """
        <picture>
          <source srcset="./media_1d5b.jpg?width=2000&#x26;format=webply&#x26;optimize=medium">
          <img alt="" src="./media_14c1.jpg?width=750&#x26;format=jpg&#x26;optimize=medium">
        </picture>
        """;

    // when
    publishPage(page1Path, page1Content);

    // then: wait for expected events to be published
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.getFirst(),
        "/page1.html",
        """
            <picture>
              <source srcset="/media_1d5b.jpg_width_2000_format_webply_optimize_medium.jpg">
              <img alt="" src="/media_14c1.jpg_width_750_format_jpg_optimize_medium.jpg">
            </picture>
            """
    );

    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);
    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>(
            "/media_14c1.jpg_width_750_format_jpg_optimize_medium.jpg",
            "https://www.my-eds-server.com/media_14c1.jpg?width=750&format=jpg&optimize=medium"),
        new ImmutablePair<>(
            "/media_1d5b.jpg_width_2000_format_webply_optimize_medium.jpg",
            "https://www.my-eds-server.com/media_1d5b.jpg?width=2000&format=webply&optimize=medium")));
  }

  @Test
  void shouldProcessPageWithSingleExternalLink() {
    // given
    final String pagePath = "page.html";
    final String pageContent = "<img src='http://www.goggle.com/image.jpg'>";

    // when
    publishPage(pagePath, pageContent);

    // then: wait for expected events to be published
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(1);
    assertPublishedDownloadRequest(downloadRequestEvents.get(0),
        "/http_www.goggle.com/image.jpg",
        "http://www.goggle.com/image.jpg");
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertPublishedPage(pageEvents.getFirst(),
        "/page.html",
        "<img src='/http_www.goggle.com/image.jpg'>");
  }

  @Test
  void shouldProcessPageWithMultipleInternalAndExternalLinks() {
    // given
    final String pagePath = "/eds/pages/page.html";
    final String initialHtml = """
        <html>
          <body>
            <img src="blogs/adventures.jpg">
            <img src="../eds-index.jpg">
            <img src="/images/logo.png">
            <img src="https://www.goggle.com/streamx-mesh-overview.jpg?par=val">
            <img src="https://www.goggle.com/assets/jcr:content/cloud.svg">
            <link rel="stylesheet" href="/stylesheets/styles.css">
            <a href="/configuration.xml">Configuration</a>
          </body>
        </html>
        """;

    // when
    publishPage(pagePath, initialHtml);

    // then: wait for expected events to be published
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(6);
    ;
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>("/eds/pages/blogs/adventures.jpg",
            "https://www.my-eds-server.com/eds/pages/blogs/adventures.jpg"),
        new ImmutablePair<>("/eds/eds-index.jpg",
            "https://www.my-eds-server.com/eds/eds-index.jpg"),
        new ImmutablePair<>("/images/logo.png", "https://www.my-eds-server.com/images/logo.png"),
        new ImmutablePair<>("/https_www.goggle.com/streamx-mesh-overview.jpg_par_val.jpg",
            "https://www.goggle.com/streamx-mesh-overview.jpg?par=val"),
        new ImmutablePair<>("/https_www.goggle.com/assets/jcr_content/cloud.svg",
            "https://www.goggle.com/assets/jcr:content/cloud.svg"),
        new ImmutablePair<>("/stylesheets/styles.css",
            "https://www.my-eds-server.com/stylesheets/styles.css")
    ));

    assertPublishedPage(pageEvents.getFirst(),
        "/eds/pages/page.html",
        """
            <html>
              <body>
                <img src="/eds/pages/blogs/adventures.jpg">
                <img src="/eds/eds-index.jpg">
                <img src="/images/logo.png">
                <img src="/https_www.goggle.com/streamx-mesh-overview.jpg_par_val.jpg">
                <img src="/https_www.goggle.com/assets/jcr_content/cloud.svg">
                <link rel="stylesheet" href="/stylesheets/styles.css">
                <a href="/configuration.xml">Configuration</a>
              </body>
            </html>
            """);
  }

  @Test
  // two independent requests in the test
  void shouldHandleRepublishedPageEditedByUser() {
    // given
    final String pagePath = "/eds/pages/page.html";
    final String pageInitialContent = """
        <img src='/eds/images/image1.jpg'>
        <img src='/eds/images/image2.jpg'>
        """;

    final String pageEditedContent = """
        <img src='/eds/images/image1.jpg'>
        <img src='/eds/images/image3.jpg'>
        """;

    // when 1
    publishPage(pagePath, pageInitialContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>("/eds/images/image1.jpg",
            "https://www.my-eds-server.com/eds/images/image1.jpg"),
        new ImmutablePair<>("/eds/images/image2.jpg",
            "https://www.my-eds-server.com/eds/images/image2.jpg")));

    assertPublishedPage(pageEvents.getFirst(),
        "/eds/pages/page.html",
        """
            <img src='/eds/images/image1.jpg'>
            <img src='/eds/images/image2.jpg'>
            """);

    // when 2: the user is publishing edited page
    publishPage(pagePath, pageEditedContent);

    // then
    downloadRequestEvents =
        waitForDownloadRequestEventsInSink(4);
    pageEvents = waitForEventsInSink(PAGE, 2);

    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>("/eds/images/image1.jpg",
            "https://www.my-eds-server.com/eds/images/image1.jpg"),
        new ImmutablePair<>("/eds/images/image3.jpg",
            "https://www.my-eds-server.com/eds/images/image3.jpg"),
        new ImmutablePair<>("/eds/images/image2.jpg",
            "https://www.my-eds-server.com/eds/images/image2.jpg"),
        new ImmutablePair<>("/eds/images/image1.jpg",
            "https://www.my-eds-server.com/eds/images/image1.jpg")));

    assertPublishedPage(pageEvents.get(1),
        "/eds/pages/page.html",
        """
            <img src='/eds/images/image1.jpg'>
            <img src='/eds/images/image3.jpg'>
            """);

  }

  @Test
  void shouldSkipProcessingOwnReference() {
    // given
    String pagePath = "/page.html";
    String pageContent = "<img src='page.html'>";

    // when
    CloudEvent publishedPage = publishPage(pagePath, pageContent);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertSameEvents(pageEvents.getFirst(), publishedPage);
  }

  @Test
  void shouldNotProcessReferencedPagesAndProcessLinkedXmlResources() {
    // given
    final String pagePath = "/pages/page.html";
    final String pageContent = """
        <a href='page2.html'>Page 2</a>
        <img src='configuration.xml'>
        <img src='page3.html'>
        """;

    // when
    publishPage(pagePath, pageContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(2);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertPublishedDownloadRequest(downloadRequestEvents.getFirst(),
        "/pages/configuration.xml",
        "https://www.my-eds-server.com/pages/configuration.xml");
    assertPublishedPage(pageEvents.getFirst(), pagePath,
        """
            <a href='page2.html'>Page 2</a>
            <img src='/pages/configuration.xml'>
            <img src='/pages/page3.html'>
            """);
  }


  @Test
  void shouldGracefullySkipDownloadingNonHttpUrls() {
    // given
    final String pagePath = "/page1.html";
    final String pageContent = "<img src='about:error'>";

    // when
    publishPage(pagePath, pageContent);

    // then: expecting nothing to be downloaded and no edits in page content
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.getFirst(), pagePath, pageContent);
    verifyNoDownloadRequestsSent();
  }

  @Test
  void shouldProcessInvalidRelativeUrls() {
    // given
    final String pagePath = "/pages/page.html";
    final String pageContent = """
        <html>
          <body>
            <img src="image with spaces and invalid chars like ^.jpg">
            <img src="http://server.com/image with spaces.jpg">
            <img src='../assets/Cube Images/cube1.webp'>
            <img src='../assets/Cube-Images/cube2.webp'>
            <img src='https://www.my-eds-server.com/assets/Cube Images/cube3.webp'>
          </body>
        </html>
        """;

    // when
    publishPage(pagePath, pageContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(5);

    assertPublishedDownloadRequests(downloadRequestEvents, List.of(
        new ImmutablePair<>("/pages/image_20with_20spaces_20and_20invalid_20chars_20like_20_5E.jpg",
            "https://www.my-eds-server.com/pages/image%20with%20spaces%20and%20invalid%20chars%20like%20%5E.jpg"),
        new ImmutablePair<>("/http_server.com/image_20with_20spaces.jpg",
            "http://server.com/image%20with%20spaces.jpg"),
        new ImmutablePair<>("/assets/Cube_20Images/cube1.webp",
            "https://www.my-eds-server.com/assets/Cube%20Images/cube1.webp"),
        new ImmutablePair<>("/assets/Cube-Images/cube2.webp",
            "https://www.my-eds-server.com/assets/Cube-Images/cube2.webp"),
        new ImmutablePair<>("/assets/Cube_20Images/cube3.webp",
            "https://www.my-eds-server.com/assets/Cube%20Images/cube3.webp")));

    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.getFirst(),
        "/pages/page.html",
        """
            <html>
              <body>
                <img src="/pages/image_20with_20spaces_20and_20invalid_20chars_20like_20_5E.jpg">
                <img src="/http_server.com/image_20with_20spaces.jpg">
                <img src='/assets/Cube_20Images/cube1.webp'>
                <img src='/assets/Cube-Images/cube2.webp'>
                <img src='/assets/Cube_20Images/cube3.webp'>
              </body>
            </html>
            """
    );
  }

  @Test
  void shouldNotProcessExternalUrlWithInvalidHostname() {
    // given
    final String pagePath = "/pages/page.html";
    final String pageContent = "<a href='http://s e r v e r . c o m/page2.html'>Page 2</a>";

    // when
    publishPage(pagePath, pageContent);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.getFirst(), pagePath, pageContent);
  }

  @Test
  void shouldPublishToSanitizedPaths() {
    // given
    final String pagePath = "/eds/pages/page%20with%20space.html";
    final String pageContent = "<img src='/Nested%20Images/image.jpg'>";

    // when
    publishPage(pagePath, pageContent);

    // then
    List<CloudEvent> downloadRequestEvents =
        waitForDownloadRequestEventsInSink(1);

    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertPublishedDownloadRequest(downloadRequestEvents.getFirst(), "/Nested_20Images/image.jpg",
        "https://www.my-eds-server.com/Nested%20Images/image.jpg");

    assertPublishedPage(pageEvents.getFirst(), "/eds/pages/page_20with_20space.html",
        "<img src='/Nested_20Images/image.jpg'>");
  }

  @Test
  void shouldSkipExternalResourcesHavingExcludedPathPatterns() {
    // given
    final String pagePath = "/eds/pages/page.html";
    final String html = """
        <html>
          <body>
            <a href="blocks/block1.html">Block 1</a>
            <link href="https://data.com/fonts/my-font.bin">
            <img src="/icons/logo.png">
            <script src="https://www.my-eds-server.com/resources/scripts/third-party.js"></script>
            <link rel="stylesheet" href="styles/styles.css">
            <a href="dir1/dir2/helix-query.yaml">The helix-query file</a>
          </body>
        </html>
        """;

    // when
    publishPage(pagePath, html);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.getFirst(), pagePath, html);
    verifyNoDownloadRequestsSent();

  }

  @Test
  void shouldRelayPageThatHasMatchingPathButHasNoExternalResources() {
    // given
    String pagePath = "/eds/pages/single-page.html";
    String html = """
        <html>
          <body>
            <div>
              Hello world!
            </div>
          </body>
        </html>
        """;

    // when
    CloudEvent publishPageEvent = publishPage(pagePath, html);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // assert the event is unchanged
    CloudEvent relayedPageEvent = pageEvents.getFirst();
    assertSameEvents(relayedPageEvent, publishPageEvent);
  }

  @Test
  void shouldRelayUnpublishRequests() {
    // given
    String pagePath = "/eds/pages/page.html";

    // when
    CloudEvent unpublishPageEvent = unpublishPage(pagePath);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // assert the event is unchanged
    CloudEvent relayedPageEvent = pageEvents.getFirst();
    assertSameEvents(relayedPageEvent, unpublishPageEvent);
  }

  @Test
  void shouldRelayRequestWithNullPayload() {
    // given
    String pagePath = "/eds/pages/page.html";

    // when
    CloudEvent event = CloudEventUtils.eventWithoutData(pagePath, Page.TYPE_PUBLISHED);
    resourcesChannel.send(event);

    // then
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(resourcesSink.received()).hasSize(1)
    );

    // assert the event is unchanged
    CloudEvent relayedPageEvent = resourcesSink.received().getFirst().getPayload();
    assertSameEvents(relayedPageEvent, event);
  }

  @Test
  void shouldRelayPageThatHasNotMatchingPayloadType() {
    // given
    String pagePath = "/pages/page.html";
    String html = """
        <html>
          <body>
            <a href="blogs/adventures.html">Adventure blogs</a>
            <img src="/images/logo.png">
            <a href="https://www.goggle.com/streamx-mesh-overview.html?par=val">StreamX Mesh</a>
            <img src="https://www.goggle.com/assets/jcr:content/cloud.svg">
          </body>
        </html>
        """;

    // when
    String payloadType = "page/experience-fragment";
    CloudEvent publishPageEvent = publishPage(pagePath, html, payloadType);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(payloadType, 1);

    // assert event is unchanged
    CloudEvent relayedPageEvent = pageEvents.getFirst();
    assertSameEvents(relayedPageEvent, publishPageEvent);
  }
}
