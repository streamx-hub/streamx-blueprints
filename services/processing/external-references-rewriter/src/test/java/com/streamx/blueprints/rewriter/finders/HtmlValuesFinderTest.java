package com.streamx.blueprints.rewriter.finders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

class HtmlValuesFinderTest {

  private static final HtmlValuesFinder htmlValuesFinder = new HtmlValuesFinder();
  private static final Logger log = Logger.getLogger(HtmlValuesFinderTest.class);

  // values for current test
  private String html;
  private List<String> xpaths;
  private Set<String> foundValues;
  private Exception exception;

  @Test
  void shouldFindAllImagePaths() {
    givenHtmlBodyContent("""
        <img src='a.png'>
        <div>
          <img src='b.png'>
          <span>
            <img src='c.png'>
          </span>
        </div>
        """)
        .andGivenXpath("//img/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "c.png");
  }

  @Test
  void shouldFindOnlyNonGoogleImagePaths() {
    givenHtmlBodyContent("""
        <img src='a.png'>
        <img src='http://www.google.com/logo.png'>
        <img src='b.png'>
        <img src='http://www.streamx.com/logo.png'>
        """)
        .andGivenXpath("//img[not(contains(@src, '://www.google.com/'))]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "http://www.streamx.com/logo.png");
  }

  @Test
  void shouldFindPathOfLastImage() {
    givenHtmlBodyContent("""
        <img src='a.png'>
        <img src='b.png'>
        <img src='c.png'>
        """)
        .andGivenXpath("//img[last()]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("c.png");
  }

  @Test
  void shouldFindPathsOfEvenImages() {
    givenHtmlBodyContent("""
        <img src='a.png'>
        <img src='b.png'>
        <img src='c.png'>
        <img src='d.png'>
        <img src='e.png'>
        """)
        .andGivenXpath("//img[position() mod 2 = 0]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("b.png", "d.png");
  }

  @Test
  void shouldFindValuesWithAppendedFixedValue() {
    givenHtmlBodyContent("<header data-header-path='http://www.google.com/header' />")
        .andGivenXpath("concat(//header/@data-header-path, '.plain.html')")
        .whenFindMatchingValues()
        .thenExpectFoundValues("http://www.google.com/header.plain.html");
  }

  @Test
  void shouldFindImagePathByImageId() {
    givenHtmlBodyContent("""
        <img id=1 src='a.png'>
        <img id=2 src='b.png'>
        <img id=3 src='c.png'>
        """)
        .andGivenXpath("//img[@id = '2']/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldFindImagePathByAbsoluteXpath() {
    givenHtml("""
        <html>
          <body>
            <div>
              <img src='a.png'>
            </div>
            <div>
              <p>
                <img src='b.png'>
              </p>
            </div>
              <span>
                <img src='c.png'>
              </span>
          </body>
        </html>
        """)
        .andGivenXpath("/html/body/div[2]/p/img/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldFindValuesBySiblingAttributeValue() {
    givenHtml("""
        <html>
          <head>
            <meta property="og:title" content="The title">
            <meta property="og:url" content="https://my-server.com/article.html">
            <meta property="og:image" content="http://my-server.com/image.png">
            <meta property="og:image:secure_url" content="https://my-server.com/image.png">
            <meta name="twitter:card" content="summary_large_image">
            <meta name="twitter:title" content="The twitter title">
            <meta name="twitter:image" content="http://my-server.com/twitter-image.png">
          </head>
        </html>
        """)
        .andGivenXpath("//meta[@property='og:image' or @name='twitter:image']/@content")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "http://my-server.com/image.png",
            "http://my-server.com/twitter-image.png"
        );
  }

  @Test
  void shouldFindAllImagesFromBothSrcAndSrcsetAttributes() {
    givenHtmlBodyContent(String.join("",
        "<img src='/content/page-1/image_777.coreimg.jpeg/999/lava-rock-formation.jpeg'",
        "  srcset='",
        "/content/page-1/image_777.coreimg.85.320.jpeg/999/lava-rock-formation.jpeg 320w,",
        "/content/page-1/image_777.coreimg.85.480.jpeg/999/lava-rock-formation.jpeg 480w,",
        "/content/page-1/image_777.coreimg.85.600.jpeg/999/lava-rock-formation.jpeg 600w,",
        "/content/page-1/image_777.coreimg.85.800.jpeg/999/lava-rock-formation.jpeg 800w,",
        "/content/page-1/image_777.coreimg.85.1024.jpeg/999/lava-rock-formation.jpeg 1024w,",
        "/content/page-1/image_777.coreimg.85.1200.jpeg/999/lava-rock-formation.jpeg 1200w,",
        "/content/page-1/image_777.coreimg.85.1600.jpeg/999/lava-rock-formation.jpeg 1600w",
        "'  height='509'  alt='Gray lava rock formation' />"))
        .andGivenXpaths("//img/@src", "//img/@srcset")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "/content/page-1/image_777.coreimg.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.320.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.480.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.600.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.800.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.1024.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.1200.jpeg/999/lava-rock-formation.jpeg",
            "/content/page-1/image_777.coreimg.85.1600.jpeg/999/lava-rock-formation.jpeg"
        );
  }

  @Test
  void shouldFindAllImagesFromPictureTag() {
    givenHtmlBodyContent("""
            <div>
              <picture>
                <source type="image/webp" srcset="./image1.png" media="(min-width: 600px)">
                <source type="image/webp" srcset="./image2.png">
                <source type="image/png" srcset="./image3.png" media="(min-width: 600px)">
                <img loading="lazy" alt="" src="./image4.png" width="965" height="600">
              </picture>
            </div>
        """)
        .andGivenXpaths("//picture/source/@srcset", "//picture/img/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "./image1.png",
            "./image2.png",
            "./image3.png",
            "./image4.png"
        );
  }

  @Test
  void shouldNotThrowExceptionWhenNoSelectorsProvided() {
    givenHtml("<html />")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidHtml() {
    givenHtml("foobar")
        .andGivenXpath("//*")
        .whenFindMatchingValues()
        .thenExpectFoundValue("foobar");
  }

  @Test
  void shouldNotThrowExceptionForInvalidXpath() {
    givenHtml("any")
        .andGivenXpath("//div[is-foo(@class, 'foo')]")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  private HtmlValuesFinderTest givenHtml(String html) {
    this.html = html;
    return this;
  }

  private HtmlValuesFinderTest givenHtmlBodyContent(String htmlBodyContent) {
    this.html = """
        <html>
          <body>
            bodyContent
          </body>
        </html>
        """.replace("bodyContent", htmlBodyContent);
    return this;
  }

  private HtmlValuesFinderTest andGivenXpath(String xpath) {
    this.xpaths = List.of(xpath);
    return this;
  }

  private HtmlValuesFinderTest andGivenXpaths(String... xpaths) {
    this.xpaths = List.of(xpaths);
    return this;
  }

  private HtmlValuesFinderTest whenFindMatchingValues() {
    try {
      this.foundValues = htmlValuesFinder.findMatchingValues(html, xpaths);
    } catch (Exception ex) {
      log.warn("Exception", ex);
      this.exception = ex;
    }
    return this;
  }

  private void thenExpectFoundValue(String expectedValue) {
    assertThat(exception).isNull();
    assertThat(foundValues).containsOnly(expectedValue);
  }

  private void thenExpectFoundValues(String... expectedValues) {
    assertThat(exception).isNull();
    assertThat(foundValues).containsExactly(expectedValues);
  }

  private void thenExpectNoFoundValues() {
    assertThat(exception).isNull();
    assertThat(foundValues).isEmpty();
  }
}