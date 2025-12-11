package com.streamx.blueprints.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.SitemapGeneratorIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class SitemapGeneratorIT extends BaseQuarkusIntegrationTest {

  private static final String BASE_URL = "https://www.streamx.dev";
  private static final String EXPECTED_SITEMAP_FILE_NAME = "/sitemaps/www.streamx.dev/sitemap.xml";

  @Test
  void shouldGenerateSitemap() {
    // given
    Page page = new Page("<b>Hello World</b>", "test-page");
    String pageKey = "pages/test-page.html";
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(pageKey, Page.TYPE_PUBLISHED, page);

    // when
    sendEvent(sourceEvent, Channels.INCOMING_PAGES);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.OUTGOING_SITEMAPS);
    assertOutgoingEvent(outgoingEvent, pageKey);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, String sourcePagePath) {
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("sitemap-generator");
    assertThat(outgoingEvent.getSubject()).isEqualTo(EXPECTED_SITEMAP_FILE_NAME);
    assertThat(outgoingEvent.getType()).isEqualTo(WebResource.TYPE_PUBLISHED);

    var outgoingSitemap = CloudEventUtils.getData(outgoingEvent, WebResource.class);
    assertThat(outgoingSitemap).isNotNull();
    assertThat(outgoingSitemap.getContentAsString()).isEqualTo("""
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        <url>
        <loc>%s/%s</loc>
        </url>
        </urlset>""".formatted(BASE_URL, sourcePagePath));
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      return Map.of(
          "streamx.blueprints.sitemap-generator.base-url", BASE_URL,
          "streamx.blueprints.sitemap-generator.dirty-check.interval", "1s",
          "streamx.blueprints.sitemap-generator.dirty-check.delay", "1s"
      );
    }
  }
}