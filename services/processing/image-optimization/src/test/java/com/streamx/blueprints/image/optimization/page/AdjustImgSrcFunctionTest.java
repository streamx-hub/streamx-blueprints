package com.streamx.blueprints.image.optimization.page;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.image.optimization.image.AssetActionStore;
import com.streamx.blueprints.image.optimization.image.OptimizeImageFunction;
import com.streamx.blueprints.image.optimization.image.OptimizedImagePathsService;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdjustImgSrcFunctionTest {

  private static final File IMAGES_DIR = new File("src/test/resources/images");
  private static final File PNG_FILE = new File(IMAGES_DIR, "ds.pNg");
  private static final File JPG_FILE = new File(IMAGES_DIR, "mesh.jpg");
  private static final File GIF_FILE = new File(IMAGES_DIR, "streamx logo.gif");

  private InMemorySource<Message<Page>> pageChannel;
  private InMemorySink<Page> pagesSink;

  private InMemorySource<Message<Asset>> imagesChannel;
  private InMemorySink<Asset> imagesSink;

  @InjectSpy
  AssetActionStore assetActionStore;

  @InjectSpy
  OptimizeImageFunction optimizeImageFunction;

  @Inject
  OptimizedImagePathsService optimizedImagePathsService;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    pageChannel = connector.source(AdjustImgSrcFunction.INCOMING_CHANNEL);
    pagesSink = connector.sink(AdjustImgSrcFunction.OUTGOING_CHANNEL);
    pagesSink.clear();

    imagesChannel = connector.source(OptimizeImageFunction.INCOMING_ASSETS_CHANNEL);
    imagesSink = connector.sink(OptimizeImageFunction.OPTIMIZED_ASSETS_CHANNEL);
    imagesSink.clear();
  }

  @BeforeEach
  void simulateOptimizedImagesArePublishedToInputTopic() {
    // OptimizeImageFunction should be configured to send messages with optimized images
    // to the same topic as the topic where the original image messages were initially read from.
    // Since InMemoryConnector operates on channels, not topics, it's not able to reproduce that.
    // Simulate this operation manually here:
    doAnswer(invocationOnMock -> {
      String optimizedImagePath = invocationOnMock.getArgument(0, String.class);
      List<Message<Asset>> receivedAssets = imagesSink.received().stream()
          .filter(msg -> extractKey(msg).equals(optimizedImagePath))
          .collect(Collectors.toList());
      if (receivedAssets.isEmpty()) {
        return null;
      }
      Message<Asset> lastMessage = Iterables.getLast(receivedAssets);
      return extractAction(lastMessage).getValue();
    }).when(assetActionStore).getOptimizedImageAction(anyString());
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
        "  Image 1: <img src='%s' />".formatted(PNG_FILE.getPath()),

        // note: multiple lines tag should be tolerated while searching for img src to be adjusted
        "  Image 2: <img  src =",
        "                  '%s' />".formatted(JPG_FILE.getPath()),

        // note: unclosed tag should be tolerated while searching for img src to be adjusted
        "  Image 3: <img src='%s'>".formatted(GIF_FILE.getPath()),

        // note: non-matching closing tag should be tolerated
        "</body>"
    );

    // when: publish the page
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // then: expect the adjusted page to be published
    Message<Page> adjustedPageMessage = waitForAdjustedOutputMessage(pageMessage);
    assertThat(extractAction(adjustedPageMessage)).isEqualTo(PUBLISH);
    assertThat(extractKey(adjustedPageMessage)).isEqualTo(pagePath);

    // and: verify its content (the correct image paths should be adjusted)
    String adjustedHtml = extractPageHtml(adjustedPageMessage);
    assertThat(adjustedHtml)
        .isNotEqualTo(html)
        .contains(
            PNG_FILE.getPath(),
            JPG_FILE.getPath().replace(".jpg", "-optimized.webp"),
            GIF_FILE.getPath().replace(".gif", "-optimized.webp")
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
    String html = "<html><img src ='%s' /></html>".formatted(JPG_FILE.getPath());

    // and: publish the page
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);
    waitForAdjustedOutputMessage(pageMessage);
    pagesSink.clear();

    // when: simulate user edits the same page (removes html tags and changes images to other one):
    publishOptimizedImage(PNG_FILE);
    String editedHtml = "<img src ='%s' />".formatted(PNG_FILE.getPath());

    // and: the page is published
    Message<Page> editedPageMessage = createPublishPageMessage(pagePath, editedHtml);
    pageChannel.send(editedPageMessage);

    // then: expect the edited page to have img src adjusted to use the optimized image
    Message<Page> adjustedEditedPageMessage = waitForAdjustedOutputMessage(editedPageMessage);
    assertThat(extractAction(adjustedEditedPageMessage)).isEqualTo(PUBLISH);
    assertThat(extractKey(adjustedEditedPageMessage)).isEqualTo(pagePath);
    assertThat(extractPageHtml(adjustedEditedPageMessage)).isEqualTo("""
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
    String html = "<html><img src='%s?param=value' /></html>".formatted(JPG_FILE.getPath());

    // when: publish the page
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // then
    Message<Page> adjustedPageMessage = waitForAdjustedOutputMessage(pageMessage);
    assertThat(extractPageHtml(adjustedPageMessage)).isEqualTo("""
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
        """.formatted(JPG_FILE.getPath());

    // when: publish the page
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // then
    Message<Page> adjustedPageMessage = waitForAdjustedOutputMessage(pageMessage);
    assertThat(extractPageHtml(adjustedPageMessage)).isEqualTo(
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
    String html = "<html><img src ='%s' /></html>".formatted(JPG_FILE.getPath());

    // when: publish the page
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldNotAdjustImgSrc_ToOptimizedImage_IfOptimizingImageHasFailed() throws IOException {
    // given: attempt to optimize invalid image file
    File invalidJpgFile = new File(IMAGES_DIR, "text-file-with-jpg-extension.jpg");
    String invalidJpgFilePath = invalidJpgFile.getPath();

    Asset asset = new Asset(FileUtils.readFileToByteArray(invalidJpgFile));
    Metadata metadata = createMetadata(invalidJpgFilePath, PUBLISH);
    imagesChannel.send(Message.of(asset, metadata));

    verify(optimizeImageFunction, timeout(500)).process(
        asset,
        Key.of(invalidJpgFilePath),
        PUBLISH,
        metadata.get(EventTime.class).get()
    );

    // when: publish page that references this invalid image
    String pagePath = "/pages/page.html";
    String html = "<html><img src ='%s' /></html>".formatted(invalidJpgFilePath);

    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldNotAdjustAlreadyAdjustedPage() throws IOException {
    // given: perform standard page adjusting
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src='" + PNG_FILE.getPath() + "' /></html>";
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);
    pageChannel.send(pageMessage);

    // make sure the page was adjusted
    Message<Page> adjustedPageMessage = waitForAdjustedOutputMessage(pageMessage);
    assertThat(extractPageHtml(adjustedPageMessage)).contains("-optimized.webp");
    pagesSink.clear();

    // when: simulate the service picks up the adjusted page again
    pageChannel.send(adjustedPageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(adjustedPageMessage);
  }

  @Test
  void shouldNotAdjustPageWithAlreadyOptimizedImages() {
    // given
    String pagePath = "/pages/page.html";
    String html = "<html><img src='src/test/resources/images/mesh-optimized.png' /></html>";
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);

    // when
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldNotAdjustPageThatDoesNotMatchPagePathsPattern() throws IOException {
    // given
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/sites/site.html";
    String html = "<html><img src='%s' /></html>".formatted(PNG_FILE.getPath());
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);

    // when
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldNotAdjustEmptyPage() {
    // given
    String pagePath = "/pages/page.html";
    String html = "";
    Message<Page> pageMessage = createPublishPageMessage(pagePath, html);

    // when
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldNotAdjustNullPage() {
    // given
    String pagePath = "/pages/null-page.html";
    Message<Page> pageMessage = createPublishPageMessage(pagePath, null);

    // when
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  @Test
  void shouldRelayUnpublishPageMessage() throws IOException {
    // given
    publishOptimizedImage(PNG_FILE);

    String pagePath = "/pages/page.html";
    String html = "<html><img src='%s' /></html>".formatted(PNG_FILE.getPath());
    Message<Page> pageMessage = createPageIngestionMessage(pagePath, html, UNPUBLISH);

    // when
    pageChannel.send(pageMessage);

    // then
    assertPageMessageIsRelayedWithNoAdjustments(pageMessage);
  }

  private static Message<Page> createPublishPageMessage(String pagePath, String html) {
    return createPageIngestionMessage(pagePath, html, PUBLISH);
  }

  private static Message<Page> createPageIngestionMessage(String pagePath, String html,
      Action action) {
    Page payload = Optional.ofNullable(html)
        .map(Page::new)
        .orElseGet(() -> new Page((ByteBuffer) null));
    Metadata metadata = createMetadata(pagePath, action);
    return Message.of(payload, metadata);
  }

  private static Metadata createMetadata(String resourcePath, Action action) {
    return Metadata.of(
        Key.of(resourcePath),
        EventTime.of(System.currentTimeMillis()),
        action
    );
  }

  private Message<Page> waitForAdjustedOutputMessage(Message<Page> inputMessage) {
    Message<Page> publishedMessage = retrievePublishedMessage();

    assertThat(extractPageHtml(publishedMessage))
        .isNotEqualTo(extractPageHtml(inputMessage));

    assertSameMetadata(inputMessage, publishedMessage);

    return publishedMessage;
  }

  private void assertPageMessageIsRelayedWithNoAdjustments(Message<Page> inputMessage) {
    Message<Page> publishedMessage = retrievePublishedMessage();

    assertThat(extractPageHtml(publishedMessage))
        .isEqualTo(extractPageHtml(inputMessage));

    assertSameMetadata(inputMessage, publishedMessage);
  }

  private Message<Page> retrievePublishedMessage() {
    await().untilAsserted(() ->
        assertThat(pagesSink.received())
            .hasSize(1)
    );

    return Iterables.getOnlyElement(pagesSink.received());
  }

  private static String extractPageHtml(Message<Page> message) {
    return Optional.ofNullable(message.getPayload())
        .map(Page::getContent)
        .map(ByteBuffer::array)
        .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
        .orElse(null);
  }

  private void publishOptimizedImage(File imageFile) throws IOException {
    // given
    Message<Asset> imageMessage = Message.of(
        new Asset(FileUtils.readFileToByteArray(imageFile)),
        createMetadata(imageFile.getPath(), PUBLISH)
    );

    // when
    imagesChannel.send(imageMessage);

    // then: assert optimized image is produced
    verifyOptimizedImageMessageIsReceived(imageFile, PUBLISH);
  }

  private void unpublishImage(File imageFile) {
    // given
    Message<Asset> imageMessage = Message.of(
        null,
        createMetadata(imageFile.getPath(), UNPUBLISH)
    );

    // when
    imagesChannel.send(imageMessage);

    // then: assert optimized image is unpublished
    verifyOptimizedImageMessageIsReceived(imageFile, UNPUBLISH);
  }

  private void verifyOptimizedImageMessageIsReceived(File imageFile, Action action) {
    String expectedOptimizedImagePath = optimizedImagePathsService
        .computePathForOptimizedImage(imageFile.getPath());

    await().untilAsserted(() -> {
      Stream<? extends Message<Asset>> matchingMessages = imagesSink.received()
          .stream()
          .filter(msg -> extractKey(msg).equals(expectedOptimizedImagePath))
          .filter(msg -> extractAction(msg).equals(action));
      assertThat(matchingMessages).hasSize(1);
    });
  }

  private static void assertSameMetadata(Message<Page> message1, Message<Page> message2) {
    assertSameMetadataValues(
        message1.getMetadata(), message2.getMetadata(),
        Key.class,
        Action.class,
        EventTime.class
    );
  }

  private static void assertSameMetadataValues(Metadata metadata1, Metadata metadata2,
      Class<?>... metadataFieldTypes) {
    for (Class<?> metadataFieldType : metadataFieldTypes) {
      assertThat(metadata1.get(metadataFieldType))
          .isEqualTo(metadata2.get(metadataFieldType));
    }
  }

}
