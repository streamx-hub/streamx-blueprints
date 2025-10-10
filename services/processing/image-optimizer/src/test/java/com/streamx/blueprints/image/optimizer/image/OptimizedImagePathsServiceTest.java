package com.streamx.blueprints.image.optimizer.image;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class OptimizedImagePathsServiceTest {

  @Inject
  OptimizedImagePathsService optimizedImagePathsService;

  @ParameterizedTest
  @CsvSource(delimiterString = " | ", textBlock = """
      path/to/file.jpg             | path/to/file-optimized.webp
      file.jpg                     | file-optimized.webp
      path/to/file.jpg?param=value | path/to/file-optimized.webp?param=value
      file.jpg?param=value         | file-optimized.webp?param=value
      """)
  void shouldComputePathForOptimizedImage(String inputPath, String expectedOutputPath) {
    assertThat(optimizedImagePathsService.computePathForOptimizedImage(inputPath))
        .isEqualTo(expectedOutputPath);
  }

}