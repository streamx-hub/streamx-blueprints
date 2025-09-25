package dev.streamx.blueprints.externalresources.contentadjusters;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.blueprints.externalresources.data.ExternalResource;
import java.util.Set;
import org.junit.jupiter.api.Test;

class XmlContentAdjusterTest extends BaseContentAdjusterTest {

  public XmlContentAdjusterTest() {
    super(new XmlContentAdjuster());
  }

  @Test
  void shouldAdjustLinks() {
    // given
    String inputXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>https://example.com/dir</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page.html</loc>
          </url>
          <url>
            <loc>https://example-com/dir</loc>
          </url>
          <url>
            <loc>https://example.com/dir2</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page2.html</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page2.htmlx</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page21.html</loc>
          </url>
          <url>
              <loc>http://www.goggle.com/page1.html</loc>
          </url>
          <url>
              <loc>http://www.goggle.com/page2.html</loc>
          </url>
          <url isDuplicate='true'>
            <loc>https://example.com/dir</loc>
          </url>
        </urlset>
        """;

    // when
    String adjustedContent = adjustLinks(
        inputXml,
        Set.of(
            new ExternalResource(
                "https://example.com/dir", "url1", "/my.com/dir"),
            new ExternalResource(
                "https://example.com/dir/page2.html", "url2", "/my.com/dir/page2.html")
        )
    );

    // then
    assertThat(adjustedContent).isEqualTo("""
        <?xml version="1.0" encoding="utf-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>/my.com/dir</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page.html</loc>
          </url>
          <url>
            <loc>https://example-com/dir</loc>
          </url>
          <url>
            <loc>https://example.com/dir2</loc>
          </url>
          <url>
            <loc>/my.com/dir/page2.html</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page2.htmlx</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page21.html</loc>
          </url>
          <url>
              <loc>http://www.goggle.com/page1.html</loc>
          </url>
          <url>
              <loc>http://www.goggle.com/page2.html</loc>
          </url>
          <url isDuplicate='true'>
            <loc>/my.com/dir</loc>
          </url>
        </urlset>
        """
    );
  }

  @Test
  void shouldDoNoAdjustmentsWhenNoMatchingLinks() {
    // given
    String inputXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>https://example.com/dir</loc>
          </url>
          <url>
            <loc>https://example.com/dir/page1.html</loc>
          </url>
        </urlset>
        """;

    // when
    String adjustedContent = adjustLinks(
        inputXml,
        Set.of(new ExternalResource("https://www.streamx.com", "url1", "/streamx.com/dir"))
    );

    // then
    assertThat(adjustedContent).isEqualTo(inputXml);
  }

}