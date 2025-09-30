package com.streamx.blueprints.image.optimization.image;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.fail;

import com.sksamuel.scrimage.webp.WebpWriter;
import com.streamx.blueprints.image.optimization.image.exceptions.NotAnImageException;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ImageOptimizerTest {

  private static final Logger log = LoggerFactory.getLogger(ImageOptimizerTest.class);
  private static final File imagesDir = new File("src/test/resources/images");
  private static final File pngImage = new File(imagesDir, "ds.pNg");
  private static final File jpgImage = new File(imagesDir, "mesh.jpg");
  private static final File gifImage = new File(imagesDir, "streamx logo.gif");

  private static List<File> imagesExpectedToHaveSmallerSizeInBytesAfterOptimization() {
    return List.of(pngImage, jpgImage);
  }

  private static List<File> imagesExpectedToHaveBiggerSizeInBytesAfterOptimization() {
    // note: it is usually hard to optimize size of an already optimized formats such as gif files
    return List.of(gifImage);
  }

  private static final int SPEED = 6;
  private static final int QUALITY = 75;
  private static final int METHOD = 4;
  private static final boolean LOSSLESS = false;
  private static final boolean NO_ALPHA = false;
  private static final boolean MULTI_THREAD = true;

  private final ImageOptimizer imageOptimizer = new ImageOptimizer(new WebpWriter(
      SPEED, QUALITY, METHOD, LOSSLESS, NO_ALPHA, MULTI_THREAD
  ));

  @ParameterizedTest
  @MethodSource("imagesExpectedToHaveSmallerSizeInBytesAfterOptimization")
  void shouldOptimizeImage_AndReduceSizeInBytes(File testImage) throws Exception {
    // given
    byte[] originalImageBytes = FileUtils.readFileToByteArray(testImage);

    // when
    byte[] optimizedImageBytes = imageOptimizer.asWebpImage(originalImageBytes);

    // then
    assertImageIsOptimized(optimizedImageBytes, originalImageBytes);
    assertThat(optimizedImageBytes).hasSizeLessThan(originalImageBytes.length);

    // and
    assertImageOptimizationIsDeterministic(originalImageBytes, optimizedImageBytes);
  }

  @ParameterizedTest
  @MethodSource("imagesExpectedToHaveBiggerSizeInBytesAfterOptimization")
  void shouldOptimizeImage_AndIncreaseSizeInBytes(File testImage) throws Exception {
    // given
    byte[] originalImageBytes = FileUtils.readFileToByteArray(testImage);

    // when
    byte[] optimizedImageBytes = imageOptimizer.asWebpImage(originalImageBytes);

    // then:
    assertImageIsOptimized(optimizedImageBytes, originalImageBytes);
    assertThat(optimizedImageBytes).hasSizeGreaterThan(originalImageBytes.length);

    // and
    assertImageOptimizationIsDeterministic(originalImageBytes, optimizedImageBytes);
  }

  private void assertImageOptimizationIsDeterministic(byte[] originalImageBytes,
      byte[] optimizedImageBytes1) throws IOException {
    byte[] optimizedImageBytes2 = imageOptimizer.asWebpImage(originalImageBytes);
    byte[] optimizedImageBytes3 = imageOptimizer.asWebpImage(originalImageBytes);

    assertEqualBytes(optimizedImageBytes2, optimizedImageBytes1);
    assertEqualBytes(optimizedImageBytes3, optimizedImageBytes2);
  }

  private static List<byte[]> bytesOfNonImageFiles() {
    return Arrays.asList(
        "Lorem ipsum".getBytes(UTF_8),
        new byte[]{0, 1, 2},
        new byte[0],
        null
    );
  }

  @ParameterizedTest
  @MethodSource("bytesOfNonImageFiles")
  void shouldNotOptimizeBytesOfNonImageFile(byte[] bytesToOptimize) {
    // when
    Throwable exception = catchThrowable(
        () -> imageOptimizer.asWebpImage(bytesToOptimize)
    );

    // then
    assertThat(exception)
        .isNotNull()
        .isInstanceOf(NotAnImageException.class);
  }

  @Test
  void imageOptimizerShouldBeThreadSafe() throws IOException {
    // given
    final int numberOfThreads = 100;
    byte[] imageBytes = FileUtils.readFileToByteArray(pngImage);

    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    List<byte[]> optimizedImages = new CopyOnWriteArrayList<>();

    // when
    IntStream
        .rangeClosed(1, numberOfThreads)
        .forEach(i ->
            executorService.execute(() ->
                optimizedImages.add(optimizeImage(imageBytes))
            )
        );

    waitForAllThreadsToFinishExecuting(executorService);

    // then
    assertThat(optimizedImages).hasSize(numberOfThreads);

    // expect every image to be optimized
    optimizedImages.forEach(optimizedBytes ->
        assertImageIsOptimized(optimizedBytes, imageBytes)
    );

    // expect every optimized version of the source image to be the same
    byte[] firstOptimizedImageBytes = optimizedImages.get(0);
    optimizedImages.forEach(optimizedBytes ->
        assertEqualBytes(optimizedBytes, firstOptimizedImageBytes)
    );
  }

  private byte[] optimizeImage(byte[] fileBytes) {
    try {
      return imageOptimizer.asWebpImage(fileBytes);
    } catch (Exception ex) {
      return fail(ex);
    }
  }

  private void assertImageIsOptimized(byte[] optimizedBytes, byte[] originalBytes) {
    assertThat(optimizedBytes).isNotEqualTo(originalBytes);
  }

  private void assertEqualBytes(byte[] bytes1, byte[] bytes2) {
    assertThat(bytes1).isEqualTo(bytes2);
  }

  private static void waitForAllThreadsToFinishExecuting(ExecutorService executorService) {
    executorService.shutdown();
    while (!executorService.isTerminated()) {
      log.info("Waiting for all threads to finish...");
      ThreadUtils.sleepQuietly(Duration.ofMillis(100));
    }
  }

}