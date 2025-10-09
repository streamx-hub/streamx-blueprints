package com.streamx.blueprints.image.optimizer.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.image.optimizer.Channels;
import com.streamx.blueprints.image.optimizer.image.AssetEventTypeStore;
import com.streamx.blueprints.image.optimizer.image.OptimizeImageFunction;
import com.streamx.blueprints.image.optimizer.image.OptimizedImagePathsService;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdjustImgSrcFunctionTest {

  private static final File IMAGES_DIR = new File("src/test/resources/images");
  private static final File PNG_FILE = new File(IMAGES_DIR, "ds.pNg");
  private static final File JPG_FILE = new File(IMAGES_DIR, "mesh.jpg");
  private static final File GIF_FILE = new File(IMAGES_DIR, "streamx logo.gif");

  private static final Set<String> PAGE_EVENTS = Set.of(Page.TYPE_PUBLISHED, Page.TYPE_UNPUBLISHED);

  private InMemorySource<CloudEvent> pagesChannel;
  private InMemorySource<CloudEvent> assetsChannel;
  private InMemorySink<CloudEvent> outgoingPagesSink;
  private InMemorySink<CloudEvent> optimizedAssetsSink;

  @InjectSpy
  AssetEventTypeStore assetEventTypeStore;

  @InjectSpy
  OptimizeImageFunction optimizeImageFunction;

  @Inject
  OptimizedImagePathsService optimizedImagePathsService;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    pagesChannel = connector.source(Channels.INCOMING_PAGES);
    assetsChannel = connector.source(Channels.INCOMING_ASSETS);
    outgoingPagesSink = connector.sink(Channels.OUTGOING_PAGES);
    outgoingPagesSink.clear();
    optimizedAssetsSink = connector.sink(Channels.OPTIMIZED_ASSETS);
    optimizedAssetsSink.clear();
  }

  @BeforeEach
  void simulateOptimizedImagesArePublishedToInputTopic() {
    // OptimizeImageFunction should be configured to send events with optimized images
    // to the same topic as the topic where the original image events were initially read from.
    // Since InMemoryConnector operates on channels, not topics, it's not able to reproduce that.
    // Simulate this operation manually here:
    doAnswer(invocationOnMock -> {
      String optimizedImagePath = invocationOnMock.getArgument(0, String.class);
      List<CloudEvent> receivedEvents = optimizedAssetsSink.received().stream()
          .map(Message::getPayload)
          .filter(event -> CloudEventUtils.getSubject(event).equals(optimizedImagePath))
          .collect(Collectors.toList());
      if (receivedEvents.isEmpty()) {
        return null;
      }
      return Iterables.getLast(receivedEvents).getType();
    }).when(assetEventTypeStore).getOptimizedImageEventType(anyString());
  }

  @Test
  void shouldAdjustImgSrc() throws IOException {
    // given: publish two of the three test images - note: PNG file is not published
    publishOptimizedImage(JPG_FILE);
    publishOptimizedImage(GIF_FILE);

    // and: prepare html of page that references all three images
    String pagePath = "/pages/page.html";
    String html = String.join("\n",
        "<html>",

        // note: this image was not optimized yet
        "  Image 1: <img src='%s' />".formatted(normalizedPath(PNG_FILE)),

        // note: multiple lines tag should be tolerated while searching for img src to be adjusted
        "  Image 2: <img  src =",
        "                  '%s' />".formatted(normalizedPath(JPG_FILE)),

        // note: unclosed tag should be tolerated while searching for img src to be adjusted
        "  Image 3: <img src='%s'>".formatted(normalizedPath(GIF_FILE)),

        // note: non-matching closing tag should be tolerated
        "</body>"
    );

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then: expect the adjusted page to be published
    CloudEvent adjustedPageEvent = waitForAdjustedOutputEvent(pageEvent);
    assertThat(adjustedPageEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(adjustedPageEvent.getSubject()).isEqualTo(pagePath);

    // and: verify its content (the correct image paths should be adjusted)
    String adjustedHtml = extractPageHtml(adjustedPageEvent);
    assertThat(adjustedHtml)
        .isNotEqualTo(html)
        .contains(
            normalizedPath(PNG_FILE),
            normalizedPath(JPG_FILE).replace(".jpg", "-optimized.webp"),
            normalizedPath(GIF_FILE).replace(".gif", "-optimized.webp")
        );

    // and: the current library re-formats (and improves) html code
    assertThat(adjustedHtml.lines())
        .containsExactly(
            "<html>",
            " <head></head>",
            " <body>",
            "  Image 1: <img src=\"src/test/resources/images/ds.pNg\"> "
            + "Image 2: <img src=\"src/test/resources/images/mesh-optimized.webp\"> "
            + "Image 3: <img src=\"src/test/resources/images/streamx logo-optimized.webp\">",
            " </body>",
            "</html>"
        );
  }

  @Test
  void shouldAdjustImgSrcOnEveryPublicationOfTheSamePage() throws IOException {
    // given: prepare optimized image and page to be published (referencing that image)
    publishOptimizedImage(JPG_FILE);
    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(normalizedPath(JPG_FILE));

    // and: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);
    waitForAdjustedOutputEvent(pageEvent);
    outgoingPagesSink.clear();

    // when: simulate user edits the same page (removes html tags and changes images to other one):
    publishOptimizedImage(PNG_FILE);
    String editedHtml = "<img src ='%s' />".formatted(normalizedPath(PNG_FILE));

    // and: the page is published
    CloudEvent editedPageEvent = createPublishPageEvent(pagePath, editedHtml);
    pagesChannel.send(editedPageEvent);

    // then: expect the edited page to have img src adjusted to use the optimized image
    CloudEvent adjustedEditedPageEvent = waitForAdjustedOutputEvent(editedPageEvent);
    assertThat(adjustedEditedPageEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(adjustedEditedPageEvent.getSubject()).isEqualTo(pagePath);
    assertThat(extractPageHtml(adjustedEditedPageEvent)).isEqualTo("""
        <html>
         <head></head>
         <body>
          <img src="src/test/resources/images/ds-optimized.webp">
         </body>
        </html>"""
    );
  }

  @Test
  void shouldAdjustImgSrc_AlsoWhenImgSrcHasQueryStringParams() throws IOException {
    // given
    publishOptimizedImage(JPG_FILE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src='%s?param=value' /></html>".formatted(normalizedPath(JPG_FILE));

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    CloudEvent adjustedPageEvent = waitForAdjustedOutputEvent(pageEvent);
    assertThat(extractPageHtml(adjustedPageEvent)).isEqualTo("""
        <html>
         <head></head>
         <body>
          <img src="src/test/resources/images/mesh-optimized.webp?param=value">
         </body>
        </html>"""
    );
  }

  @Test
  void shouldAdjustImgSrc_AlsoWhenDocumentContainsOtherImagesWhichAreNotOptimized()
      throws IOException {
    // given
    publishOptimizedImage(JPG_FILE);

    String pagePath = "/pages/page.html";
    String html = """
        <html>
          <img src="not-optimized-image.jpg"/>
          <img src="%s"/>
          <img src="not-optimized-image.jpg"/>
        </html>
        """.formatted(normalizedPath(JPG_FILE));

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    CloudEvent adjustedPageEvent = waitForAdjustedOutputEvent(pageEvent);
    assertThat(extractPageHtml(adjustedPageEvent)).isEqualTo(
        """
            <html>
             <head></head>
             <body>
              \
            <img src="not-optimized-image.jpg"> \
            <img src="src/test/resources/images/mesh-optimized.webp"> \
            <img src="not-optimized-image.jpg">
             </body>
            </html>"""
    );
  }

  @Test
  void shouldNotAdjustImgSrc_ToOptimizedImage_ThatWasOncePublished_ButIsNowUnpublished()
      throws IOException {
    // given: simulate the image once had its optimized version, but it's no longer available
    publishOptimizedImage(JPG_FILE);
    unpublishImage(JPG_FILE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(normalizedPath(JPG_FILE));

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustImgSrc_ToOptimizedImage_IfOptimizingImageHasFailed() throws IOException {
    // given: attempt to optimize invalid image file
    File invalidJpgFile = new File(IMAGES_DIR, "text-file-with-jpg-extension.jpg");

    CloudEvent publishAssetEvent = createPublishAssetEvent(invalidJpgFile);
    assetsChannel.send(publishAssetEvent);

    verify(optimizeImageFunction, timeout(500)).process(publishAssetEvent);

    // when: publish page that references this invalid image
    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(normalizedPath(invalidJpgFile));

    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustAlreadyAdjustedPage() throws IOException {
    // given: perform standard page adjusting
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src='" + normalizedPath(PNG_FILE) + "' /></html>";
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // make sure the page was adjusted
    CloudEvent adjustedPageEvent = waitForAdjustedOutputEvent(pageEvent);
    assertThat(extractPageHtml(adjustedPageEvent)).contains("-optimized.webp");
    outgoingPagesSink.clear();

    // when: simulate the service picks up the adjusted page again
    pagesChannel.send(adjustedPageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(adjustedPageEvent);
  }

  @Test
  void shouldNotAdjustPageWithAlreadyOptimizedImages() {
    // given
    String pagePath = "/pages/page.html";
    String html = "<html><img src='src/test/resources/images/mesh-optimized.png' /></html>";
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustPageThatDoesNotMatchPagePathsPattern() throws IOException {
    // given
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/sites/site.html";
    String html = "<html><img src='%s' /></html>".formatted(normalizedPath(PNG_FILE));
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustEmptyPage() {
    // given
    String pagePath = "/pages/page.html";
    String html = "";
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustNullPage() {
    // given
    String pagePath = "/pages/null-page.html";
    CloudEvent pageEvent = createPublishPageEvent(pagePath, null);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldRelayUnpublishPageEvent() throws IOException {
    // given
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/pages/page.html";
    CloudEvent pageEvent = createUnpublishPageEvent(pagePath);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  private static CloudEvent createPublishPageEvent(String pagePath, String html) {
    return CloudEventUtils.eventWithData(new Page(html), Page.TYPE_PUBLISHED, pagePath);
  }

  private static CloudEvent createPublishAssetEvent(File assetFile) throws IOException {
    String path = normalizedPath(assetFile);
    byte[] content = FileUtils.readFileToByteArray(assetFile);
    return CloudEventUtils.eventWithData(new Asset(content), Asset.TYPE_PUBLISHED, path);
  }

  private static CloudEvent createUnpublishPageEvent(String pagePath) {
    return CloudEventUtils.eventWithData(new Page((ByteBuffer) null), Page.TYPE_UNPUBLISHED,
        pagePath);
  }

  private static CloudEvent createUnpublishAssetEvent(File assetFile) {
    return CloudEventUtils.eventWithData(new Asset((ByteBuffer) null), Asset.TYPE_UNPUBLISHED,
        normalizedPath(assetFile));
  }

  private CloudEvent waitForAdjustedOutputEvent(CloudEvent inputEvent) {
    CloudEvent publishedEvent = retrievePublishedEvent();

    assertThat(extractPageHtml(publishedEvent))
        .isNotEqualTo(extractPageHtml(inputEvent));

    assertSameMetadata(inputEvent, publishedEvent);

    return publishedEvent;
  }

  private void assertPageEventIsRelayedWithNoAdjustments(CloudEvent inputEvent) {
    CloudEvent publishedEvent = retrievePublishedEvent();

    assertThat(extractPageHtml(publishedEvent))
        .isEqualTo(extractPageHtml(inputEvent));

    assertSameMetadata(inputEvent, publishedEvent);
  }

  private CloudEvent retrievePublishedEvent() {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(getOutgoingPages()).hasSize(1)
    );

    return Iterables.getOnlyElement(getOutgoingPages());
  }

  private static String extractPageHtml(CloudEvent event) {
    return Optional.ofNullable(CloudEventUtils.getData(event, Page.class))
        .map(Page::getContent)
        .map(ByteBuffer::array)
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .orElse(null);
  }

  private void publishOptimizedImage(File imageFile) throws IOException {
    // given
    CloudEvent imageEvent = createPublishAssetEvent(imageFile);

    // when
    assetsChannel.send(imageEvent);

    // then: assert optimized image is produced
    verifyOptimizedImageEventIsReceived(imageFile, Asset.TYPE_PUBLISHED);
  }

  private void unpublishImage(File imageFile) {
    // given
    CloudEvent imageEvent = createUnpublishAssetEvent(imageFile);

    // when
    assetsChannel.send(imageEvent);

    // then: assert optimized image is unpublished
    verifyOptimizedImageEventIsReceived(imageFile, Asset.TYPE_UNPUBLISHED);
  }

  private void verifyOptimizedImageEventIsReceived(File imageFile, String eventType) {
    String expectedOptimizedImagePath = optimizedImagePathsService
        .computePathForOptimizedImage(normalizedPath(imageFile));

    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      Stream<CloudEvent> matchingEvents = optimizedAssetsSink.received()
          .stream()
          .map(Message::getPayload)
          .filter(event -> CloudEventUtils.getSubject(event).equals(expectedOptimizedImagePath))
          .filter(event -> eventType.equals(event.getType()));
      assertThat(matchingEvents).hasSize(1);
    });
  }

  private static void assertSameMetadata(CloudEvent event1, CloudEvent event2) {
    assertThat(event1.getSubject()).isEqualTo(event2.getSubject());
    assertThat(event1.getType()).isEqualTo(event2.getType());
    assertThat(event1.getTime()).isEqualTo(event2.getTime());
  }

  private List<CloudEvent> getOutgoingPages() {
    return outgoingPagesSink.received().stream()
        .map(Message::getPayload)
        .filter(event -> PAGE_EVENTS.contains(event.getType()))
        .toList();
  }

  private static String normalizedPath(File file) {
    return file.getPath().replace('\\', '/');
  }
}
