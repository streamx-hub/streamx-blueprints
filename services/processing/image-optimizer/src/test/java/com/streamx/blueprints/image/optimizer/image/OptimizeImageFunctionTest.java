package com.streamx.blueprints.image.optimizer.image;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.data.Asset;
import dev.streamx.quasar.reactive.messaging.Store.Entry;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import dev.streamx.quasar.reactive.messaging.utils.MetadataUtils;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class OptimizeImageFunctionTest {

  private static final File IMAGES_DIR = new File("src/test/resources/images");
  private static final File PNG_FILE = new File(IMAGES_DIR, "ds.pNg");
  private static final File JPG_FILE = new File(IMAGES_DIR, "mesh.jpg");
  private static final File GIF_FILE = new File(IMAGES_DIR, "streamx logo.gif");
  private static final File TEXT_FILE = new File(IMAGES_DIR, "text-file.txt");

  private static List<File> imageFiles() {
    return List.of(PNG_FILE, JPG_FILE, GIF_FILE);
  }

  private final List<String> rejectedImagesPaths = new ArrayList<>();

  private InMemorySource<Message<Asset>> channel;
  private InMemorySink<Asset> sink;

  @InjectSpy
  OptimizeImageFunction optimizeImageFunction;

  @InjectSpy
  AssetActionStore assetActionStore;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    channel = connector.source(OptimizeImageFunction.INCOMING_ASSETS_CHANNEL);
    sink = connector.sink(OptimizeImageFunction.OPTIMIZED_ASSETS_CHANNEL);
    sink.clear();
  }

  @BeforeEach
  void configureOptimizeImageFunctionToSaveRejectedImagesToList() {
    doAnswer(invocationOnMock -> {
      try {
        return invocationOnMock.callRealMethod();
      } catch (Exception ex) {
        rejectedImagesPaths.add(invocationOnMock.getArgument(1, Key.class).getValue());
        throw ex;
      }
    }).when(optimizeImageFunction)
        .process(any(Asset.class), any(Key.class), any(Action.class), any(EventTime.class));
  }

  @ParameterizedTest
  @MethodSource("imageFiles")
  void shouldOptimizeImage(File imageFile) throws IOException {
    // given
    Message<Asset> imageMessage = createPublishFileMessage(imageFile);

    // when
    channel.send(imageMessage);

    // then: expect the optimized image to be published
    Message<Asset> optimizedImageMessage = assertMessageIsProcessed(imageMessage);
    assertAction(optimizedImageMessage, PUBLISH);
    assertOptimizedFileIsRenamed(imageFile, optimizedImageMessage);

    // and: verify its content (should be optimized - have different bytes)
    byte[] optimizedImageBytes = extractImageBytes(optimizedImageMessage);
    byte[] originalImageBytes = FileUtils.readFileToByteArray(imageFile);
    assertThat(optimizedImageBytes).isNotEqualTo(originalImageBytes);
  }

  @Test
  void shouldUnpublishOptimizedImageAlongWithOriginalImage() throws IOException {
    // given: image was published along with its optimized version:
    File imageFile = PNG_FILE;
    Message<Asset> publishImageMessage = createPublishFileMessage(imageFile);
    channel.send(publishImageMessage);
    assertMessageIsProcessed(publishImageMessage);
    sink.clear();

    // when
    Message<Asset> unpublishImageMessage = createUnpublishFileMessage(imageFile);
    channel.send(unpublishImageMessage);

    // then: expect the optimized image to be unpublished
    Message<Asset> optimizedImageMessage = assertMessageIsProcessed(unpublishImageMessage);
    assertAction(optimizedImageMessage, UNPUBLISH);
    assertOptimizedFileIsRenamed(imageFile, optimizedImageMessage);

    // and: verify its content - should be null
    byte[] optimizedImageBytes = extractImageBytes(optimizedImageMessage);
    assertThat(optimizedImageBytes).isNull();
  }

  private static void assertAction(Message<Asset> optimizedImageMessage, Action expectedAction) {
    Action actualAction = extractAction(optimizedImageMessage);
    assertThat(actualAction).isEqualTo(expectedAction);
  }

  private static void assertOptimizedFileIsRenamed(File sourceImage,
      Message<Asset> optimizedImageMessage) {
    String filePathWithoutExtension = StringUtils.substringBeforeLast(sourceImage.getPath(), ".");
    String expectedOptimizedImagePath = filePathWithoutExtension + "-optimized.webp";
    String actualOptimizedImagePath = extractKey(optimizedImageMessage);
    assertThat(actualOptimizedImagePath).isEqualTo(expectedOptimizedImagePath);
  }

  @Test
  void shouldNotOptimizeAlreadyOptimizedImage() throws IOException {
    // given: perform standard image optimization
    Message<Asset> imageMessage = createPublishFileMessage(PNG_FILE);
    channel.send(imageMessage);
    Message<Asset> optimizedImageMessage = assertMessageIsProcessed(imageMessage);
    sink.clear();

    // when: simulate the service picks up the optimized image again
    channel.send(optimizedImageMessage);

    // then
    assertImageIsNotPublishedDueToValidationNotPassed();
  }

  @Test
  void shouldNotOptimizeImageOfUnsupportedExtension() throws IOException {
    // given
    Message<Asset> textFileMessage = createPublishFileMessage(TEXT_FILE);

    // when
    channel.send(textFileMessage);

    // then
    assertImageIsNotPublishedDueToValidationNotPassed();
  }

  @Test
  void shouldNotOptimizeFileWithoutExtension() throws IOException {
    // given
    File testFile = new File(IMAGES_DIR, "file-without-extension");
    Message<Asset> testFileMessage = createPublishFileMessage(testFile);

    // when
    channel.send(testFileMessage);

    // then
    assertImageIsNotPublishedDueToValidationNotPassed();
  }

  @Test
  void shouldNotOptimizeTextFileHavingJpgExtension() throws IOException {
    // given
    File testFile = new File(IMAGES_DIR, "text-file-with-jpg-extension.jpg");
    Message<Asset> testFileMessage = createPublishFileMessage(testFile);

    // when
    channel.send(testFileMessage);

    // then
    assertImageIsNotPublishedDueToException(testFileMessage);
  }

  @Test
  void shouldNotOptimizeThatDoesNotMatchFilePathsPattern() throws IOException {
    // given
    File testFile = new File("src/test/resources/ds.png");
    Message<Asset> testFileMessage = createPublishFileMessage(testFile);

    // when
    channel.send(testFileMessage);

    // then
    assertImageIsNotPublishedDueToValidationNotPassed();
  }

  private static Message<Asset> createPublishFileMessage(File file) throws IOException {
    byte[] payload = FileUtils.readFileToByteArray(file);
    return createIngestionMessage(PUBLISH, file, new Asset(payload));
  }

  private static Message<Asset> createUnpublishFileMessage(File file) {
    return createIngestionMessage(UNPUBLISH, file, null);
  }

  private static Message<Asset> createIngestionMessage(Action action, File file, Asset payload) {
    Metadata metadata = createMetadata(file, action);
    return Message.of(payload, metadata);
  }

  private static Metadata createMetadata(File file, Action action) {
    return Metadata.of(
        Key.of(file.getPath()),
        EventTime.of(System.currentTimeMillis()),
        action
    );
  }

  private Message<Asset> assertMessageIsProcessed(Message<Asset> inputMessage) {
    Entry<String> expectedEntry = new Entry<>(
        MetadataUtils.extractKey(inputMessage),
        MetadataUtils.extractAction(inputMessage).getValue()
    );

    await().untilAsserted(() -> {
      assertThat(assetActionStore.getAssetActionByKey().entries())
          .contains(expectedEntry);

      assertThat(sink.received())
          .hasSize(1);
    });

    return Iterables.getOnlyElement(sink.received());
  }

  private void assertImageIsNotPublishedDueToValidationNotPassed() {
    assertImageIsNotPublished();
    assertThat(rejectedImagesPaths).isEmpty();
  }

  private void assertImageIsNotPublishedDueToException(Message<Asset> inputMessage) {
    assertImageIsNotPublished();
    assertThat(rejectedImagesPaths).hasSize(1);
    assertThat(rejectedImagesPaths.get(0)).isEqualTo(extractKey(inputMessage));
  }

  private void assertImageIsNotPublished() {
    await().atLeast(Duration.ofMillis(100)).untilAsserted(() ->
        assertThat(sink.received()).isEmpty()
    );
  }

  private static byte[] extractImageBytes(Message<Asset> message) {
    return Optional.ofNullable(message.getPayload())
        .map(Asset::getContent)
        .map(ByteBuffer::array)
        .orElse(null);
  }

}
