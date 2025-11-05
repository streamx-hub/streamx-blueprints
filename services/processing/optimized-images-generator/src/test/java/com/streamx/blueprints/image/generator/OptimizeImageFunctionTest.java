package com.streamx.blueprints.image.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.OptimizedAsset;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class OptimizeImageFunctionTest {

  private static final String ASSET_TYPE = "assets/image";

  private static final File IMAGES_DIR = new File("src/test/resources/images");
  private static final File PNG_FILE = new File(IMAGES_DIR, "ds.pNg");
  private static final File JPG_FILE = new File(IMAGES_DIR, "mesh.jpg");
  private static final File GIF_FILE = new File(IMAGES_DIR, "streamx logo.gif");
  private static final File TEXT_FILE = new File(IMAGES_DIR, "text-file.txt");

  private static List<File> imageFiles() {
    return List.of(PNG_FILE, JPG_FILE, GIF_FILE);
  }

  private InMemorySource<CloudEvent> channel;
  private InMemorySink<CloudEvent> sink;

  @Inject
  OptimizeImageFunction optimizeImageFunction;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    channel = connector.source(Channels.INCOMING_ASSETS);
    sink = connector.sink(Channels.OPTIMIZED_ASSETS);
    sink.clear();
  }

  @ParameterizedTest
  @CsvSource(delimiterString = " | ", textBlock = """
      path/to/file.jpg | true
      path/to/file     | false
      path/to/.jpg     | false
      file.jpg         | true
      .jpg             | false
      file.            | false
      file             | false
      """)
  void shouldTellIfIsValidFileName(String inputPath, boolean expectedValid) {
    assertThat(OptimizeImageFunction.isValidFileName(inputPath)).isSameAs(expectedValid);
  }

  @ParameterizedTest
  @CsvSource(delimiterString = " | ", textBlock = """
      path/to/file.jpg             | path/to/file-optimized.webp
      file.jpg                     | file-optimized.webp
      """)
  void shouldComputePathForOptimizedImage(String inputPath, String expectedOutputPath) {
    assertThat(optimizeImageFunction.computePathForOptimizedImage(inputPath))
        .isEqualTo(expectedOutputPath);
  }

  @ParameterizedTest
  @MethodSource("imageFiles")
  void shouldOptimizeImage(File imageFile) throws IOException {
    // given
    CloudEvent imageEvent = createPublishAssetEvent(imageFile);

    // when
    channel.send(imageEvent);

    // then: expect the optimized image to be published
    CloudEvent optimizedImageEvent = waitForOptimizedImageEvent();
    assertOptimizedImage(optimizedImageEvent, imageEvent);

    // and: verify its content (should be optimized - have different bytes)
    byte[] optimizedImageBytes = extractImageBytes(optimizedImageEvent);
    byte[] originalImageBytes = FileUtils.readFileToByteArray(imageFile);
    assertThat(optimizedImageBytes).isNotEqualTo(originalImageBytes);
  }

  @Test
  void shouldUnpublishOptimizedImageAlongWithOriginalImage() throws IOException {
    // given: image was published along with its optimized version:
    File imageFile = PNG_FILE;
    CloudEvent publishImageEvent = createPublishAssetEvent(imageFile);
    channel.send(publishImageEvent);
    waitForOptimizedImageEvent();
    sink.clear();

    // when
    CloudEvent unpublishImageEvent = createUnpublishAssetEvent(imageFile);
    channel.send(unpublishImageEvent);

    // then: expect the optimized image to be unpublished
    CloudEvent optimizedImageEvent = waitForOptimizedImageEvent();
    assertOptimizedImage(optimizedImageEvent, unpublishImageEvent);

    // and: verify its content - should be null
    byte[] optimizedImageBytes = extractImageBytes(optimizedImageEvent);
    assertThat(optimizedImageBytes).isNull();
  }

  private static void assertOptimizedImage(CloudEvent optimizedImage, CloudEvent inputImage) {
    assertOptimizedEventType(optimizedImage, inputImage);
    assertOptimizedAssetType(optimizedImage, inputImage);
    assertOptimizedFileIsRenamed(optimizedImage, inputImage);
  }

  private static void assertOptimizedEventType(CloudEvent optimizedImage, CloudEvent inputImage) {
    String actualEventType = optimizedImage.getType();
    String expectedEventType = inputImage.getType()
        .replace(Asset.TYPE_PUBLISHED, OptimizedAsset.TYPE_PUBLISHED)
        .replace(Asset.TYPE_UNPUBLISHED, OptimizedAsset.TYPE_UNPUBLISHED);
    assertThat(actualEventType).isEqualTo(expectedEventType);
  }

  private static void assertOptimizedAssetType(CloudEvent optimizedImage, CloudEvent inputImage) {
    if (Asset.TYPE_PUBLISHED.equals(inputImage.getType())) {
      assertThat(CloudEventUtils.getData(optimizedImage, OptimizedAsset.class))
          .isNotNull()
          .extracting(OptimizedAsset::getType)
          .isEqualTo(ASSET_TYPE);
    } else {
      assertThat(optimizedImage.getData()).isNull();
    }
  }

  private static void assertOptimizedFileIsRenamed(CloudEvent optimizedImage,
      CloudEvent inputImage) {
    String originalFilePath = inputImage.getSubject();
    String filePathWithoutExtension = StringUtils.substringBeforeLast(originalFilePath, ".");
    String expectedOptimizedImagePath = filePathWithoutExtension + "-optimized.webp";
    String actualOptimizedImagePath = optimizedImage.getSubject();
    assertThat(actualOptimizedImagePath).isEqualTo(expectedOptimizedImagePath);
  }

  @Test
  void shouldNotOptimizeAlreadyOptimizedImage() throws IOException {
    // given: perform standard image optimization
    CloudEvent imageEvent = createPublishAssetEvent(PNG_FILE);
    channel.send(imageEvent);
    CloudEvent optimizedImageEvent = waitForOptimizedImageEvent();
    sink.clear();

    // when: simulate the service picks up the optimized image again
    channel.send(optimizedImageEvent);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeImageOfUnsupportedExtension() throws IOException {
    // given
    CloudEvent textFileEvent = createPublishAssetEvent(TEXT_FILE);

    // when
    channel.send(textFileEvent);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeFileWithoutExtension() throws IOException {
    // given
    File testFile = new File(IMAGES_DIR, "file-without-extension");
    CloudEvent testFileEvent = createPublishAssetEvent(testFile);

    // when
    channel.send(testFileEvent);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeTextFileHavingJpgExtension() throws IOException {
    // given
    File testFile = new File(IMAGES_DIR, "text-file-with-jpg-extension.jpg");
    CloudEvent testFileEvent = createPublishAssetEvent(testFile);

    // when
    channel.send(testFileEvent);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeImageThatDoesNotMatchFilePathsPattern() throws IOException {
    // given
    File testFile = new File("src/test/resources/ds.png");
    CloudEvent testFileEvent = createPublishAssetEvent(testFile);

    // when
    channel.send(testFileEvent);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeImageIfUnexpectedEventType() {
    // given
    String path = JPG_FILE.getPath();
    Asset asset = new Asset(new byte[]{0, 1, 2}, ASSET_TYPE);
    CloudEvent event = CloudEventUtils.eventWithData(path, Data.TYPE_PUBLISHED, asset);

    // when
    channel.send(event);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeImageIfUnexpectedPayloadType() {
    // given
    String path = JPG_FILE.getPath();
    Data data = new Data(new String(new byte[]{0, 1, 2}), "any");
    CloudEvent event = CloudEventUtils.eventWithData(path, Data.TYPE_PUBLISHED, data);

    // when
    channel.send(event);

    // then
    assertImageIsNotPublished();
  }

  @Test
  void shouldNotOptimizeImageIfNoPayload() {
    // given
    String path = JPG_FILE.getPath();
    CloudEvent event = CloudEventUtils.eventWithoutData(path, Asset.TYPE_PUBLISHED);

    // when
    channel.send(event);

    // then
    assertImageIsNotPublished();
  }

  private static CloudEvent createPublishAssetEvent(File assetFile) throws IOException {
    String path = assetFile.getPath();
    byte[] payload = FileUtils.readFileToByteArray(assetFile);
    return CloudEventUtils.eventWithData(
        path,
        Asset.TYPE_PUBLISHED,
        new Asset(payload, ASSET_TYPE)
    );
  }

  private static CloudEvent createUnpublishAssetEvent(File assetFile) {
    return CloudEventUtils.eventWithoutData(assetFile.getPath(), Asset.TYPE_UNPUBLISHED);
  }

  private CloudEvent waitForOptimizedImageEvent() {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(sink.received()).hasSize(1)
    );

    return Iterables.getOnlyElement(sink.received()).getPayload();
  }

  private void assertImageIsNotPublished() {
    await().atMost(Duration.ofSeconds(3)).atLeast(Duration.ofMillis(100)).untilAsserted(() ->
        assertThat(sink.received()).isEmpty()
    );
  }

  private static byte[] extractImageBytes(CloudEvent event) {
    return Optional.ofNullable(CloudEventUtils.getData(event, OptimizedAsset.class))
        .map(OptimizedAsset::getContent)
        .map(ByteBuffer::array)
        .orElse(null);
  }

}
