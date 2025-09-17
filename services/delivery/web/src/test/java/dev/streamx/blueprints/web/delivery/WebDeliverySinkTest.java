package dev.streamx.blueprints.web.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WebDeliverySinkTest {
  @CsvSource(delimiterString = "->", textBlock = """
      c.html     ->  c.html
      a/b/c.html ->  a/b/c.html
      c          ->  c/index.html
      a/b/c      ->  a/b/c/index.html
      a/b/c/     ->  a/b/c/index.html
      /          ->  /index.html
      ''         ->  /index.html
      """)
  @ParameterizedTest
  void shouldComputeHtmlResourcePath(String inputPath, String expectedResult) {
    String actualResult = WebDeliverySink.computeHtmlResourcePath(inputPath);
    assertEquals(expectedResult, actualResult);
  }
}
