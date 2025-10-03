package com.streamx.blueprints.rewriter.finders;

import org.junit.jupiter.api.Test;

class XmlValuesFinderTest extends AbstractValuesFinderTest<XmlValuesFinderTest> {

  private static final XmlValuesFinder xmlValuesFinder = new XmlValuesFinder();

  @Test
  void shouldFindAllImagePaths() {
    givenInput("""
        <root>
          <image src='a.png' />
          <container>
            <image src='b.png' />
            <block>
              <image src='c.png' />
            </block>
          </container>
        </root>
        """)
        .andGivenLookupPaths("//image/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "c.png");
  }

  @Test
  void shouldFindOnlyNonGoogleImagePaths() {
    givenInput("""
        <root>
          <image src='a.png' />
          <image src='http://www.google.com/logo.png' />
          <image src='b.png' />
          <image src='http://www.streamx.com/logo.png' />
        </root>
        """)
        .andGivenLookupPaths("//image[not(contains(@src, '://www.google.com/'))]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "http://www.streamx.com/logo.png");
  }

  @Test
  void shouldFindPathOfLastImage() {
    givenInput("""
        <root>
          <image src='a.png' />
          <image src='b.png' />
          <image src='c.png' />
        </root>
        """)
        .andGivenPath("//image[last()]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("c.png");
  }

  @Test
  void shouldFindPathsOfEvenImages() {
    givenInput("""
        <root>
          <image src='a.png' />
          <image src='b.png' />
          <image src='c.png' />
          <image src='d.png' />
          <image src='e.png' />
        </root>
        """)
        .andGivenPath("//image[position() mod 2 = 0]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("b.png", "d.png");
  }

  @Test
  void shouldFindImagePathByImageId() {
    givenInput("""
        <root>
          <image id='1' src='a.png' />
          <image id='2' src='b.png' />
          <image id='3' src='c.png' />
        </root>
        """)
        .andGivenPath("//image[@id = '2']/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldFindImagePathByAbsoluteXpath() {
    givenInput("""
        <root>
          <container>
            <image src='a.png' />
          </container>
          <container>
            <p>
              <image src='b.png' />
            </p>
          </container>
            <block>
              <image src='c.png' />
            </block>
        </root>
        """)
        .andGivenPath("/root/container[2]/p/image/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldNotThrowExceptionForInvalidXml() {
    givenInput("<root arg=value>")
        .andGivenPath("//*")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidXpath() {
    givenInput("<root />")
        .andGivenPath("//root[is-foo(@class, 'foo')]")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Override
  protected BaseValuesFinder getFinder() {
    return xmlValuesFinder;
  }


}