package com.streamx.blueprints.image.optimizer.image;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.streamx.blueprints.image.optimizer.image.exceptions.NotAnImageException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
class ImageOptimizerTest {

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

  @Inject
  ImageOptimizer imageOptimizer;

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

  private void assertImageIsOptimized(byte[] optimizedBytes, byte[] originalBytes) {
    assertThat(optimizedBytes).isNotEqualTo(originalBytes);
  }

  private void assertEqualBytes(byte[] bytes1, byte[] bytes2) {
    assertThat(bytes1).isEqualTo(bytes2);
  }

}