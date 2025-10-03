package com.streamx.blueprints.rewriter.contentadjusters;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.rewriter.data.ExternalResource;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonContentAdjusterTest extends BaseContentAdjusterTest {

  public JsonContentAdjusterTest() {
    super(new JsonContentAdjuster());
  }

  @Test
  void shouldAdjustLinks() {
    // given
    String inputJson = """
        {
          "data": [
            { "image": "https://example.com/dir" },
            { "image": "https://example.com/dir#anchor" },
            { "image": "https://example.com/dir/page.html" },
            { "image": "https://example.com/dir/page.html#anchor" },
            { "image": "https://example-com/dir" },
            { "image": "https://example.com/dir2" },
            { "image": "https://example.com/dir/page2.html" },
            { "image": "https://example.com/dir/page2.html#anchor" },
            { "image": "https://example.com/dir/page2.htmlx" },
            { "image": "https://example.com/dir/page21.html" },
            { "image": "" },
            { "image": null }
          ]
        }
        """;

    // when
    String adjustedContent = adjustLinks(
        inputJson,
        Set.of(
            new ExternalResource(
                "https://example.com/dir", "url1", "/my.com/dir"),
            new ExternalResource(
                "https://example.com/dir/page2.html", "url2", "/my.com/dir/page2.html")
        )
    );

    // then
    assertThat(adjustedContent).isEqualTo("""
        {
          "data": [
            { "image": "/my.com/dir" },
            { "image": "/my.com/dir#anchor" },
            { "image": "https://example.com/dir/page.html" },
            { "image": "https://example.com/dir/page.html#anchor" },
            { "image": "https://example-com/dir" },
            { "image": "https://example.com/dir2" },
            { "image": "/my.com/dir/page2.html" },
            { "image": "/my.com/dir/page2.html#anchor" },
            { "image": "https://example.com/dir/page2.htmlx" },
            { "image": "https://example.com/dir/page21.html" },
            { "image": "" },
            { "image": null }
          ]
        }
        """
    );
  }

  @Test
  void shouldDoNoAdjustmentsWhenNoMatchingLinks() {
    // given
    String inputJson = """
        {
          "data": [
            { "image": "http://www.google.com/logo.png" }
          ]
        }
        """;

    // when
    String adjustedContent = adjustLinks(
        inputJson,
        Set.of(new ExternalResource("https://www.streamx.com", "url1", "/streamx.com/dir"))
    );

    // then
    assertThat(adjustedContent).isEqualTo(inputJson);
  }

}