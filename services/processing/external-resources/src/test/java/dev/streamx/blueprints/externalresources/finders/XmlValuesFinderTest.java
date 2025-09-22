package dev.streamx.blueprints.externalresources.finders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

class XmlValuesFinderTest {

  private static final XmlValuesFinder xmlValuesFinder = new XmlValuesFinder();

  // values for current test
  private String xml;
  private List<String> xpaths;
  private Set<String> foundValues;
  private Exception exception;

  @Test
  void shouldFindAllImagePaths() {
    givenXml("""
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
        .andGivenXpath("//image/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "c.png");
  }

  @Test
  void shouldFindOnlyNonGoogleImagePaths() {
    givenXml("""
        <root>
          <image src='a.png' />
          <image src='http://www.google.com/logo.png' />
          <image src='b.png' />
          <image src='http://www.streamx.com/logo.png' />
        </root>
        """)
        .andGivenXpath("//image[not(contains(@src, '://www.google.com/'))]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("a.png", "b.png", "http://www.streamx.com/logo.png");
  }

  @Test
  void shouldFindPathOfLastImage() {
    givenXml("""
        <root>
          <image src='a.png' />
          <image src='b.png' />
          <image src='c.png' />
        </root>
        """)
        .andGivenXpath("//image[last()]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("c.png");
  }

  @Test
  void shouldFindPathsOfEvenImages() {
    givenXml("""
        <root>
          <image src='a.png' />
          <image src='b.png' />
          <image src='c.png' />
          <image src='d.png' />
          <image src='e.png' />
        </root>
        """)
        .andGivenXpath("//image[position() mod 2 = 0]/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValues("b.png", "d.png");
  }

  @Test
  void shouldFindImagePathByImageId() {
    givenXml("""
        <root>
          <image id='1' src='a.png' />
          <image id='2' src='b.png' />
          <image id='3' src='c.png' />
        </root>
        """)
        .andGivenXpath("//image[@id = '2']/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldFindImagePathByAbsoluteXpath() {
    givenXml("""
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
        .andGivenXpath("/root/container[2]/p/image/@src")
        .whenFindMatchingValues()
        .thenExpectFoundValue("b.png");
  }

  @Test
  void shouldNotThrowExceptionForInvalidXml() {
    givenXml("<root arg=value>")
        .andGivenXpath("//*")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidXpath() {
    givenXml("<root />")
        .andGivenXpath("//root[is-foo(@class, 'foo')]")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  private XmlValuesFinderTest givenXml(String xml) {
    this.xml = xml;
    return this;
  }

  private XmlValuesFinderTest andGivenXpath(String xpath) {
    this.xpaths = List.of(xpath);
    return this;
  }

  private XmlValuesFinderTest whenFindMatchingValues() {
    try {
      this.foundValues = xmlValuesFinder.findMatchingValues(xml, xpaths);
    } catch (Exception ex) {
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