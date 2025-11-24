package com.streamx.blueprints.web.server.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import com.streamx.blueprints.web.server.Channels;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.time.Duration;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(BaseQuarkusIntegrationTestProfile.class)
public class WebServerSinkIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldServePublishedPage() throws IOException {
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
    String expectedServedPageContent = """
        <html>
          <body>
            Welcome at the products page.<br />
            <!--#include file="products-list.html" -->
          </body>
        </html>
        """;

    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertUrlContent(SERVICE_BASE_URL + pagePath, expectedServedPageContent)
    );
  }

  private void assertUrlContent(String url, String expected) throws IOException {
    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpGet get = new HttpGet(url);
      CloseableHttpResponse response = http.execute(get);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_OK);
      assertThat(response.getEntity().getContent().readAllBytes())
          .asString().isEqualTo(expected);
    }
  }

}