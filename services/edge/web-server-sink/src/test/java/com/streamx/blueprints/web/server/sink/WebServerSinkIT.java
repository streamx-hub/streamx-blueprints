package com.streamx.blueprints.web.server.sink;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import com.streamx.blueprints.web.server.Channels;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(BaseQuarkusIntegrationTestProfile.class)
public class WebServerSinkIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldServePublishedPage() {
    // given
    String pagePath = "/page.html";
    String pageHtml = """
        <html>
          <body>
            Welcome at the products page.<br />
            {{#include src="products-list.html"}}
          </body>
        </html>
        """;

    // when
    Page page = new Page(pageHtml, "simple-page");
    CloudEvent pageEvent = CloudEventUtils.eventWithData(pagePath, Page.TYPE_PUBLISHED, page);
    sendEvent(pageEvent, Channels.RESOURCES);

    // then
    assertUrlContent(deploymentUrl.toString() + pagePath, """
        <html>
          <body>
            Welcome at the products page.<br />
            <!--#include file="products-list.html" -->
          </body>
        </html>
        """);
  }

  private void assertUrlContent(String url, String expected) {
    String actualContent = getUrlContent(url);
    assertThat(actualContent).isEqualTo(expected);
  }

}