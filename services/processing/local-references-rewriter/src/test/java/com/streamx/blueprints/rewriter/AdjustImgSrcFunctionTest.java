package com.streamx.blueprints.rewriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

@QuarkusTest
class AdjustImgSrcFunctionTest {

  private record Image(String originalPath, String optimizedPath) {

    private Image(String originalPath) {
      this(originalPath, originalPath + "-optimized");
    }
  }

  private static final Image PNG_IMAGE = new Image("/images/png-file.png");
  private static final Image JPG_IMAGE = new Image("/images/jpg-file.jpg");
  private static final Image GIF_IMAGE = new Image("/images/gif-file.gif");

  private static final Set<String> PAGE_EVENTS = Set.of(Page.TYPE_PUBLISHED, Page.TYPE_UNPUBLISHED);

  private InMemorySource<CloudEvent> pagesChannel;
  private InMemorySource<CloudEvent> assetsChannel;
  private InMemorySink<CloudEvent> adjustedPagesSink;

  @InjectSpy
  ImgSrcAdjuster imgSrcAdjuster;

  @Inject
  OptimizedAssetsStore optimizedAssetsStore;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    pagesChannel = connector.source(Channels.INCOMING_PAGES);
    assetsChannel = connector.source(Channels.OPTIMIZED_ASSETS);
    adjustedPagesSink = connector.sink(Channels.ADJUSTED_PAGES);
    adjustedPagesSink.clear();
    optimizedAssetsStore.clear();
  }

  @Test
  void shouldAdjustImgSrc() {
    // given: publish optimized versions of two of the three test images
    publishOptimizedImage(JPG_IMAGE);
    publishOptimizedImage(GIF_IMAGE);

    // and: prepare html of page that references all three images
    String pagePath = "/pages/page.html";
    String html = String.join("\n",
        "<html>",

        // note: this image was not optimized yet
        "  Image 1: <img src='%s' />".formatted(PNG_IMAGE.originalPath),

        // note: multiple lines tag should be tolerated while searching for img src to be adjusted
        "  Image 2: <img  src =",
        "                  '%s' />".formatted(JPG_IMAGE.originalPath),

        // note: unclosed tag should be tolerated while searching for img src to be adjusted
        "  Image 3: <img src='%s'>".formatted(GIF_IMAGE.originalPath),

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
    assertThat(adjustedHtml.lines())
        .containsExactly(
            "<html>",
            " <head></head>",
            " <body>",
            "  Image 1: <img src=\"%s\"> ".formatted(PNG_IMAGE.originalPath)
            + "Image 2: <img src=\"%s\"> ".formatted(JPG_IMAGE.optimizedPath)
            + "Image 3: <img src=\"%s\">".formatted(GIF_IMAGE.optimizedPath),
            " </body>",
            "</html>"
        );
  }

  @Test
  void shouldAdjustImgSrcOnEveryPublicationOfTheSamePage() {
    // given: prepare optimized image and page to be published (referencing that image)
    publishOptimizedImage(JPG_IMAGE);
    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(JPG_IMAGE.originalPath);

    // and: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);
    waitForAdjustedOutputEvent(pageEvent);
    adjustedPagesSink.clear();

    // when: simulate user edits the same page (removes html tags and changes images to other one):
    publishOptimizedImage(PNG_IMAGE);
    String editedHtml = "<img src ='%s' />".formatted(PNG_IMAGE.originalPath);

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
          <img src="%s">
         </body>
        </html>""".formatted(PNG_IMAGE.optimizedPath)
    );
  }

  @Test
  void shouldAdjustImgSrc_AlsoWhenImgSrcHasQueryStringParams() {
    // given
    publishOptimizedImage(JPG_IMAGE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src='%s?param=value' /></html>".formatted(JPG_IMAGE.originalPath);

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    CloudEvent adjustedPageEvent = waitForAdjustedOutputEvent(pageEvent);
    assertThat(extractPageHtml(adjustedPageEvent)).isEqualTo("""
        <html>
         <head></head>
         <body>
          <img src="%s?param=value">
         </body>
        </html>""".formatted(JPG_IMAGE.optimizedPath)
    );
  }

  @Test
  void shouldAdjustImgSrc_AlsoWhenDocumentContainsOtherImagesWhichAreNotOptimized() {
    // given
    publishOptimizedImage(JPG_IMAGE);

    String pagePath = "/pages/page.html";
    String html = """
        <html>
          <img src="not-optimized-image.jpg"/>
          <img src="%s"/>
          <img src="not-optimized-image.jpg"/>
        </html>
        """.formatted(JPG_IMAGE.originalPath);

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
            <img src="%s"> \
            <img src="not-optimized-image.jpg">
             </body>
            </html>""".formatted(JPG_IMAGE.optimizedPath)
    );
  }

  @Test
  void shouldNotAdjustImgSrc_ToOptimizedImage_ThatWasOncePublished_ButIsNowUnpublished() {
    // given: simulate the image once had its optimized version, but it's no longer available
    publishOptimizedImage(JPG_IMAGE);
    unpublishOptimizedImage(JPG_IMAGE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(JPG_IMAGE.originalPath);

    // when: publish the page
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotAdjustPageThatDoesNotMatchPagePathsPattern() {
    // given
    publishOptimizedImage(PNG_IMAGE);

    String pagePath = "/sites/site.html";
    String html = "<html><img src='%s' /></html>".formatted(PNG_IMAGE.originalPath);
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
  void shouldNotAdjustPageEventWithNoPayload() {
    // given
    String pagePath = "/pages/null-page.html";
    CloudEvent pageEvent = CloudEventUtils.eventWithoutData(pagePath, Page.TYPE_PUBLISHED);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldNotFailWhenExceptionParsingHtml() {
    // given: simulate a null content page reached processing somehow
    doAnswer(invocationOnMock -> imgSrcAdjuster.adjustPageContent(null))
        .when(imgSrcAdjuster)
        .adjustPageContent(argThat(StringUtils::isNotBlank));

    String pagePath = "/pages/page.html";
    String html = "Content is overridden in imgSrcAdjuster spy configuration";
    CloudEvent pageEvent = createPublishPageEvent(pagePath, html);

    // when
    pagesChannel.send(pageEvent);

    // then: expect the error to be caught and the event relayed
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  @Test
  void shouldRelayUnpublishPageEvent() {
    // given
    publishOptimizedImage(PNG_IMAGE);

    String pagePath = "/pages/page.html";
    CloudEvent pageEvent = createUnpublishPageEvent(pagePath);

    // when
    pagesChannel.send(pageEvent);

    // then
    assertPageEventIsRelayedWithNoAdjustments(pageEvent);
  }

  private static CloudEvent createPublishPageEvent(String pagePath, String html) {
    return CloudEventUtils.eventWithData(pagePath, Page.TYPE_PUBLISHED, new Page(html, "any"));
  }

  private static CloudEvent createUnpublishPageEvent(String pagePath) {
    return CloudEventUtils.eventWithoutData(pagePath, Page.TYPE_UNPUBLISHED);
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
        .map(Page::getContentAsString)
        .orElse(null);
  }

  private void publishOptimizedImage(Image image) {
    // given
    CloudEvent imageEvent = CloudEventUtils.eventWithData(
        image.optimizedPath,
        OptimizedAsset.TYPE_PUBLISHED,
        new OptimizedAsset(new byte[]{0, 1, 2}, "any", image.originalPath)
    );

    // when
    assetsChannel.send(imageEvent);

    // then
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(optimizedAssetsStore.getOptimizedAssetPath(image.originalPath)).isNotNull()
    );
  }

  private void unpublishOptimizedImage(Image image) {
    // given
    CloudEvent imageEvent = CloudEventUtils.eventWithoutData(
        image.optimizedPath,
        OptimizedAsset.TYPE_UNPUBLISHED
    );

    // when
    assetsChannel.send(imageEvent);

    // then
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(optimizedAssetsStore.getOptimizedAssetPath(image.originalPath)).isNull()
    );
  }

  private static void assertSameMetadata(CloudEvent event1, CloudEvent event2) {
    assertThat(event1.getSubject()).isEqualTo(event2.getSubject());
    assertThat(event1.getType()).isEqualTo(event2.getType());
    assertThat(event1.getTime()).isEqualTo(event2.getTime());

    Page page1 = CloudEventUtils.getData(event1, Page.class);
    Page page2 = CloudEventUtils.getData(event2, Page.class);
    if (!Resource.isEmpty(page1) && !Resource.isEmpty(page2)) {
      assertThat(page1.getType()).isNotNull().isEqualTo(page2.getType());
    }
  }

  private List<CloudEvent> getOutgoingPages() {
    return adjustedPagesSink.received().stream()
        .map(Message::getPayload)
        .filter(event -> PAGE_EVENTS.contains(event.getType()))
        .toList();
  }

}
