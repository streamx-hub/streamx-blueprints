package dev.streamx.blueprints.externalresources.contentadjusters;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.blueprints.externalresources.data.ExternalResource;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HtmlContentAdjusterTest extends BaseContentAdjusterTest {

  public HtmlContentAdjusterTest() {
    super(new HtmlContentAdjuster());
  }

  @Test
  void shouldAdjustLinks() {
    // given
    String inputPageHtml = """
        <a href='https://example.com/dir'>Link 1</a>
        <a href="https://example.com/dir#anchor">Link 2</a>
        <a href='https://example.com/dir/page.html'>Link 3</a>
        <a href="https://example.com/dir/page.html#anchor">Link 4</a>
        <a href=https://example.com/dir>Link with no quotes</a>
        <a href="https://example-com/dir">Link with different hostname</a>
        <a href="https://example.com/dir2">Link with different path</a>

        <a href='https://example.com/dir/page2.html'>Link to page 2</a>
        <a href='https://example.com/dir/page2.html#anchor'>Link to page 2 with anchor</a>

        <a href='https://example.com/dir/page2.htmlx'>Link to page 2 htmlx</a>
        <a href='https://example.com/dir/page21.html'>Link to page 21</a>
        """;

    // when
    String adjustedContent = adjustLinks(
        inputPageHtml,
        Set.of(
            new ExternalResource(
                "https://example.com/dir", "url1", "/my.com/dir"),
            new ExternalResource(
                "https://example.com/dir/page2.html", "url2", "/my.com/dir/page2.html")
        )
    );

    // then
    assertThat(adjustedContent).isEqualTo("""
        <a href='/my.com/dir'>Link 1</a>
        <a href="/my.com/dir#anchor">Link 2</a>
        <a href='https://example.com/dir/page.html'>Link 3</a>
        <a href="https://example.com/dir/page.html#anchor">Link 4</a>
        <a href=https://example.com/dir>Link with no quotes</a>
        <a href="https://example-com/dir">Link with different hostname</a>
        <a href="https://example.com/dir2">Link with different path</a>

        <a href='/my.com/dir/page2.html'>Link to page 2</a>
        <a href='/my.com/dir/page2.html#anchor'>Link to page 2 with anchor</a>

        <a href='https://example.com/dir/page2.htmlx'>Link to page 2 htmlx</a>
        <a href='https://example.com/dir/page21.html'>Link to page 21</a>
        """
    );
  }

  @Test
  void shouldDoNoAdjustmentsWhenNoMatchingLinks() {
    // given
    String inputPageHtml = """
        <a href='https://example.com/dir'>Link 1</a>
        <a href='https://example.com/dir/page1.html'>Link to page 1</a>
        """;

    // when
    String adjustedContent = adjustLinks(
        inputPageHtml,
        Set.of(new ExternalResource("https://www.streamx.com", "url1", "/streamx.com/dir"))
    );

    // then
    assertThat(adjustedContent).isEqualTo(inputPageHtml);
  }

}