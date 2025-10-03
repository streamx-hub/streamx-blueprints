package com.streamx.blueprints.rewriter.functions;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.rewriter.testutils.DownloadedResource;
import com.streamx.blueprints.rewriter.testutils.SkipVerifyingEachExternalResourceWasDownloadedExactlyOnce;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessPageFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessPageWithSingleInternalLink() {
    // given
    final String pagePath = "page.html";
    final String pageContent = "⭐<img src='image-🚀.jpg'>⭐";
    final byte[] imageContent = generateImageContent("image-🚀.jpg");

    // and: expect the external url to be downloaded (mock response to avoid online call)
    mockDownloadResponse(
        "https://www.my-eds-server.com/image-🚀.jpg", imageContent
    );

    // when
    publishPage(pagePath, pageContent);

    // then: wait for expected events to be published
    waitForDownloadedAssets(1);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertDownloadedAsset(0,
        "/image-_.jpg",
        imageContent);
    assertPublishedPage(pageEvents.get(0),
        "/page.html",
        "⭐<img src='/image-_.jpg'>⭐");
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
    final byte[] imageContent = generateImageContent("image.png");
    final byte[] imageCustomizedContent = generateImageContent("image.png?a=b&c=d");

    // and: expect the external url to be downloaded (mock response to avoid online call)
    mockDownloadResponses(
        "https://www.my-eds-server.com/image.png", imageContent,
        "https://www.my-eds-server.com/image.png?a=b&c=d", imageCustomizedContent
    );

    // when
    publishPage(pagePath, pageContent);

    // then: wait for expected events to be published
    waitForDownloadedAssets(2);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertDownloadedAsset(0,
        "/image.png",
        imageContent);
    assertDownloadedAsset(1,
        "/image.png_a_b_c_d.png",
        imageCustomizedContent);
    assertPublishedPage(pageEvents.get(0),
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

    // and: expect the external url to be downloaded (mock response to avoid online call)
    mockDownloadResponses(
        "https://www.my-eds-server.com/media_1d5b.jpg?width=2000&format=webply&optimize=medium",
        "media_1d5b.jpg",
        "https://www.my-eds-server.com/media_14c1.jpg?width=750&format=jpg&optimize=medium",
        "media_14c1.jpg"
    );

    // when
    publishPage(page1Path, page1Content);

    // then: wait for expected events to be published
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.get(0),
        "/page1.html",
        """
            <picture>
              <source srcset="/media_1d5b.jpg_width_2000_format_webply_optimize_medium.jpg">
              <img alt="" src="/media_14c1.jpg_width_750_format_jpg_optimize_medium.jpg">
            </picture>
            """
    );

    waitForDownloadedAssets(2);
    assertDownloadedAsset(0,
        "/media_14c1.jpg_width_750_format_jpg_optimize_medium.jpg",
        "media_14c1.jpg".getBytes(UTF_8));
    assertDownloadedAsset(1,
        "/media_1d5b.jpg_width_2000_format_webply_optimize_medium.jpg",
        "media_1d5b.jpg".getBytes(UTF_8));
  }

  @Test
  void shouldProcessPageWithSingleExternalLink() {
    // given
    final String pagePath = "page.html";
    final String pageContent = "<img src='http://www.goggle.com/image.jpg'>";
    final byte[] imageContent = generateImageContent("image.jpg");

    // and: expect the external url to be downloaded (mock response to avoid online call)
    mockDownloadResponse(
        "http://www.goggle.com/image.jpg", imageContent
    );

    // when
    publishPage(pagePath, pageContent);

    // then: wait for expected events to be published
    waitForDownloadedAssets(1);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertDownloadedAsset(0,
        "/http_www.goggle.com/image.jpg",
        imageContent);
    assertPublishedPage(pageEvents.get(0),
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
    final byte[] blogImageContent = generateImageContent("blogs/adventures.jpg");
    final byte[] edsIndexImageContent = generateImageContent("eds-index.jpg");
    final byte[] logoImageContent = generateImageContent("logo.png");
    final byte[] meshImageContent = generateImageContent("streamx-mesh-overview.jpg");
    final byte[] cloudImageContent = generateImageContent("cloud.svg");
    final String stylesheetContent = "body {color:green}";

    // and: expect these urls to be downloaded (mock responses to avoid online calls)
    mockDownloadResponses(
        "https://www.my-eds-server.com/eds/pages/blogs/adventures.jpg", blogImageContent,
        "https://www.my-eds-server.com/eds/eds-index.jpg", edsIndexImageContent,
        "https://www.my-eds-server.com/images/logo.png", logoImageContent,
        "https://www.goggle.com/streamx-mesh-overview.jpg?par=val", meshImageContent,
        "https://www.goggle.com/assets/jcr:content/cloud.svg", cloudImageContent,
        "https://www.my-eds-server.com/stylesheets/styles.css", stylesheetContent
    );

    // when
    publishPage(pagePath, initialHtml);

    // then: wait for expected events to be published
    waitForDownloadedAssets(6);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    // and: assert content of published events
    assertDownloadedAssets(List.of(
        new DownloadedResource("/eds/pages/blogs/adventures.jpg", blogImageContent),
        new DownloadedResource("/eds/eds-index.jpg", edsIndexImageContent),
        new DownloadedResource("/images/logo.png", logoImageContent),
        new DownloadedResource("/https_www.goggle.com/streamx-mesh-overview.jpg_par_val.jpg", meshImageContent),
        new DownloadedResource("/https_www.goggle.com/assets/jcr_content/cloud.svg", cloudImageContent),
        new DownloadedResource("/stylesheets/styles.css", stylesheetContent.getBytes(UTF_8))
    ));

    assertPublishedPage(pageEvents.get(0),
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
  @SkipVerifyingEachExternalResourceWasDownloadedExactlyOnce
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

    final byte[] image1Content = generateImageContent("image1.jpg");
    final byte[] image2Content = generateImageContent("image2.jpg");
    final byte[] image3Content = generateImageContent("image3.jpg");

    // and
    mockDownloadResponses(
        "https://www.my-eds-server.com/eds/images/image1.jpg", image1Content,
        "https://www.my-eds-server.com/eds/images/image2.jpg", image2Content,
        "https://www.my-eds-server.com/eds/images/image3.jpg", image3Content
    );

    // when 1
    publishPage(pagePath, pageInitialContent);

    // then
    waitForDownloadedAssets(2);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertDownloadedAsset(0, "/eds/images/image1.jpg", image1Content);
    assertDownloadedAsset(1, "/eds/images/image2.jpg", image2Content);
    assertPublishedPage(pageEvents.get(0),
        "/eds/pages/page.html",
        """
            <img src='/eds/images/image1.jpg'>
            <img src='/eds/images/image2.jpg'>
            """);

    // when 2: the user is publishing edited page
    publishPage(pagePath, pageEditedContent);

    // then
    waitForDownloadedAssets(4);
    pageEvents = waitForEventsInSink(PAGE, 2);

    assertDownloadedAsset(2, "/eds/images/image1.jpg", image1Content);
    assertDownloadedAsset(3, "/eds/images/image3.jpg", image3Content);
    assertPublishedPage(pageEvents.get(1),
        "/eds/pages/page.html",
        """
            <img src='/eds/images/image1.jpg'>
            <img src='/eds/images/image3.jpg'>
            """);

    // and
    verifyDownloadedTimes("https://www.my-eds-server.com/eds/images/image1.jpg", 2);
    verifyDownloadedOnce("https://www.my-eds-server.com/eds/images/image2.jpg");
    verifyDownloadedOnce("https://www.my-eds-server.com/eds/images/image3.jpg");
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
    assertSameEvents(pageEvents.get(0), publishedPage);
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
    final String configurationContent = "<cfg key='value'>";

    mockDownloadResponses(
        "https://www.my-eds-server.com/pages/configuration.xml", configurationContent,
        "https://www.my-eds-server.com/pages/page3.html", "<html />"
    );

    // when
    publishPage(pagePath, pageContent);

    // then
    waitForDownloadedWebResources(1);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertDownloadedWebResource(0, "/pages/configuration.xml", configurationContent);
    assertPublishedPage(pageEvents.get(0), pagePath,
        """
        <a href='page2.html'>Page 2</a>
        <img src='/pages/configuration.xml'>
        <img src='/pages/page3.html'>
        """);
  }

  @Test
  void shouldPublishPageWithChangedLinksAlsoForUndownloadableResources() {
    // given
    final String pagePath = "/eds/pages/page.html";
    final String initialHtml = """
        <html>
          <body>
            <img src="image1.jpg">
            <img src="image2.jpg">
            <img src="image3.jpg">
            <img src="image4.jpg">
          </body>
        </html>
        """;
    final byte[] image1Content = generateImageContent("image1.jpg");
    final byte[] image3Content = generateImageContent("image3.jpg");

    // and: simulate images 1 and 3 are downloaded with no errors
    mockDownloadResponses(
        "https://www.my-eds-server.com/eds/pages/image1.jpg", image1Content,
        "https://www.my-eds-server.com/eds/pages/image3.jpg", image3Content
    );

    // and: simulate images 2 and 4 are undownloadable
    mockDownloadResourceFails(
        "https://www.my-eds-server.com/eds/pages/image2.jpg",
        "https://www.my-eds-server.com/eds/pages/image4.jpg"
    );

    // when
    publishPage(pagePath, initialHtml);

    // then: wait for expected pages to be published
    waitForDownloadedAssets(2);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertDownloadedAsset(0, "/eds/pages/image1.jpg", image1Content);
    assertDownloadedAsset(1, "/eds/pages/image3.jpg", image3Content);
    assertPublishedPage(pageEvents.get(0),
        "/eds/pages/page.html",
        """
            <html>
              <body>
                <img src="/eds/pages/image1.jpg">
                <img src="/eds/pages/image2.jpg">
                <img src="/eds/pages/image3.jpg">
                <img src="/eds/pages/image4.jpg">
              </body>
            </html>
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
    assertPublishedPage(pageEvents.get(0), pagePath, pageContent);
    verifyNoDownloads();
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
    final byte[] externalImageContent = {0, 1, 2};

    // and
    mockDownloadResponses(
        "https://www.my-eds-server.com/pages/image%20with%20spaces%20and%20invalid%20chars%20like%20%5E.jpg",
        externalImageContent,
        "http://server.com/image%20with%20spaces.jpg",
        externalImageContent,
        "https://www.my-eds-server.com/assets/Cube%20Images/cube1.webp",
        externalImageContent,
        "https://www.my-eds-server.com/assets/Cube-Images/cube2.webp",
        externalImageContent,
        "https://www.my-eds-server.com/assets/Cube%20Images/cube3.webp",
        externalImageContent
    );

    // when
    publishPage(pagePath, pageContent);

    // then
    assertDownloadedAssets(List.of(
        new DownloadedResource("/pages/image_20with_20spaces_20and_20invalid_20chars_20like_20_5E.jpg", externalImageContent),
        new DownloadedResource("/http_server.com/image_20with_20spaces.jpg", externalImageContent),
        new DownloadedResource("/assets/Cube_20Images/cube1.webp", externalImageContent),
        new DownloadedResource("/assets/Cube-Images/cube2.webp", externalImageContent),
        new DownloadedResource("/assets/Cube_20Images/cube3.webp", externalImageContent)
    ));

    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    assertPublishedPage(pageEvents.get(0),
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
    assertPublishedPage(pageEvents.get(0), pagePath, pageContent);
  }

  @Test
  void shouldPublishToSanitizedPaths() {
    // given
    final String pagePath = "/eds/pages/page%20with%20space.html";
    final String pageContent = "<img src='/Nested%20Images/image.jpg'>";
    final byte[] nestedImageContent = generateImageContent("image.jpg");

    mockDownloadResponse(
        "https://www.my-eds-server.com/Nested%20Images/image.jpg", nestedImageContent
    );

    // when
    publishPage(pagePath, pageContent);

    // then
    waitForDownloadedAssets(1);
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);

    assertDownloadedAsset(0, "/Nested_20Images/image.jpg",
        nestedImageContent);
    assertPublishedPage(pageEvents.get(0), "/eds/pages/page_20with_20space.html",
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
    assertPublishedPage(pageEvents.get(0), pagePath, html);
    waitForDownloadedAssets(0);

    // and
    verifyNoDownloads();
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
    CloudEvent relayedPageEvent = pageEvents.get(0);
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
    CloudEvent relayedPageEvent = pageEvents.get(0);
    assertSameEvents(relayedPageEvent, unpublishPageEvent);
  }

  @Test
  void shouldRelayRequestWithNullPayload() {
    // given
    String pagePath = "/eds/pages/page.html";

    // when
    CloudEvent event = CloudEventUtils.eventWithoutData(Page.TYPE_PUBLISHED, pagePath);
    resourcesChannel.send(event);

    // then
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(resourcesSink.received()).hasSize(1)
    );

    // assert the event is unchanged
    CloudEvent relayedPageEvent = resourcesSink.received().get(0).getPayload();
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
    CloudEvent relayedPageEvent = pageEvents.get(0);
    assertSameEvents(relayedPageEvent, publishPageEvent);
  }

  private static byte[] generateImageContent(String imagePath) {
    return imagePath.getBytes(UTF_8);
  }
}
