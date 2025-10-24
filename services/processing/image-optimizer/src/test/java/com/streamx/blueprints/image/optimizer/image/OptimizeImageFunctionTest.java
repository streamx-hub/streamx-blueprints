package com.streamx.blueprints.image.optimizer.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Iterables;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.image.optimizer.Channels;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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

  private InMemorySource<CloudEvent> channel;
  private InMemorySink<CloudEvent> sink;

  @InjectSpy
  AssetEventTypeStore assetEventTypeStore;

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
  @MethodSource("imageFiles")
  void shouldOptimizeImage(File imageFile) throws IOException {
    // given
    CloudEvent imageEvent = createPublishAssetEvent(imageFile);

    // when
    channel.send(imageEvent);

    // then: expect the optimized image to be published
    CloudEvent optimizedImageEvent = assertEventIsProcessed(imageEvent);
    assertEventType(optimizedImageEvent, Asset.TYPE_PUBLISHED);
    assertOptimizedFileIsRenamed(imageFile, optimizedImageEvent);

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
    assertEventIsProcessed(publishImageEvent);
    sink.clear();

    // when
    CloudEvent unpublishImageEvent = createUnpublishAssetEvent(imageFile);
    channel.send(unpublishImageEvent);

    // then: expect the optimized image to be unpublished
    CloudEvent optimizedImageEvent = assertEventIsProcessed(unpublishImageEvent);
    assertEventType(optimizedImageEvent, Asset.TYPE_UNPUBLISHED);
    assertOptimizedFileIsRenamed(imageFile, optimizedImageEvent);

    // and: verify its content - should be null
    byte[] optimizedImageBytes = extractImageBytes(optimizedImageEvent);
    assertThat(optimizedImageBytes).isNull();
  }

  private static void assertEventType(CloudEvent optimizedImageEvent, String expectedEventType) {
    String actualEventType = optimizedImageEvent.getType();
    assertThat(actualEventType).isEqualTo(expectedEventType);
  }

  private static void assertOptimizedFileIsRenamed(File sourceImage,
      CloudEvent optimizedImageEvent) {
    String filePathWithoutExtension = StringUtils.substringBeforeLast(sourceImage.getPath(), ".");
    String expectedOptimizedImagePath = filePathWithoutExtension + "-optimized.webp";
    String actualOptimizedImagePath = optimizedImageEvent.getSubject();
    assertThat(actualOptimizedImagePath).isEqualTo(expectedOptimizedImagePath);
  }

  @Test
  void shouldNotOptimizeAlreadyOptimizedImage() throws IOException {
    // given: perform standard image optimization
    CloudEvent imageEvent = createPublishAssetEvent(PNG_FILE);
    channel.send(imageEvent);
    CloudEvent optimizedImageEvent = assertEventIsProcessed(imageEvent);
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
    Asset asset = new Asset(new byte[]{0, 1, 2});
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
    Data data = new Data(new byte[]{0, 1, 2});
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
    return CloudEventUtils.eventWithData(path, Asset.TYPE_PUBLISHED, new Asset(payload));
  }

  private static CloudEvent createUnpublishAssetEvent(File assetFile) {
    return CloudEventUtils.eventWithoutData(assetFile.getPath(), Asset.TYPE_UNPUBLISHED);
  }

  private CloudEvent assertEventIsProcessed(CloudEvent inputEvent) {
    Map.Entry<String, String> expectedEntry = Map.entry(
        CloudEventUtils.getSubject(inputEvent),
        inputEvent.getType()
    );

    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      assertThat(assetEventTypeStore.getAssetEventTypeByKey().entrySet()).contains(expectedEntry);
      assertThat(sink.received()).hasSize(1);
    });

    return Iterables.getOnlyElement(sink.received()).getPayload();
  }

  private void assertImageIsNotPublished() {
    await().atMost(Duration.ofSeconds(3)).atLeast(Duration.ofMillis(100)).untilAsserted(() ->
        assertThat(sink.received()).isEmpty()
    );
  }

  private static byte[] extractImageBytes(CloudEvent event) {
    return Optional.ofNullable(CloudEventUtils.getData(event, Asset.class))
        .map(Asset::getContent)
        .map(ByteBuffer::array)
        .orElse(null);
  }

}
