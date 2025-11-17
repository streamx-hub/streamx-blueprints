package com.streamx.blueprints.sitemap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.SitemapGeneratorIT.WireMockProfile;
import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@ConnectWireMock
@TestProfile(WireMockProfile.class)
public class SitemapGeneratorIT {

  private static final String INCOMING_PAGES_URL = "http://localhost:8081/"
                                                   + Channels.INCOMING_PAGES;
  private static final String OUTGOING_SITEMAPS_ENDPOINT = "/" + Channels.OUTGOING_SITEMAPS;

  private static final String BASE_URL = "https://www.streamx.dev";
  private static final String SITEMAP_FILE_NAME = "sitemap.xml";

  // will be injected automatically when the test class is annotated with @ConnectWireMock
  WireMock wiremock;

  @BeforeAll
  static void setEventSource() {
    System.setProperty("quarkus.application.name", SitemapGeneratorIT.class.getSimpleName());
  }

  @BeforeEach
  void setupEndpointForReceivingOutgoingEvents() {
    wiremock.register(post(urlEqualTo(OUTGOING_SITEMAPS_ENDPOINT))
        .willReturn(aResponse().withStatus(202)));
  }

  @Test
  void shouldGenerateSitemap() throws IOException {
    Page page = new Page("<b>Hello World</b>", "test-page");
    String pageKey = "pages/test-page.html";
    CloudEvent sourceEvent = createPublishPageEvent(pageKey, page);
    String serializedEvent = new CloudEventJsonSerializer().serialize(sourceEvent).toString();

    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost(INCOMING_PAGES_URL);
      post.setEntity(new StringEntity(serializedEvent));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }

    LoggedRequest response = waitForResponseRequest();
    byte[] body = response.getBody();
    CloudEvent outgoingEvent = new CloudEventJsonDeserializer().deserialize(Buffer.buffer(body));
    assertOutgoingEvent(outgoingEvent, pageKey);
  }

  private static CloudEvent createPublishPageEvent(String key, Page page) {
    return CloudEventUtils.eventWithData(key, Page.TYPE_PUBLISHED, page);
  }

  private static LoggedRequest waitForResponseRequest() {
    AtomicReference<LoggedRequest> result = new AtomicReference<>();
    await().untilAsserted(() -> {
      List<LoggedRequest> requests = WireMock.findAll(
          postRequestedFor(urlEqualTo(OUTGOING_SITEMAPS_ENDPOINT)));
      assertThat(requests).hasSize(1);
      result.set(requests.getFirst());
    });
    return result.get();
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, String sourcePagePath) {
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("sitemap-generator");
    assertThat(outgoingEvent.getSubject()).isEqualTo(SITEMAP_FILE_NAME);
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

  public static class WireMockProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "mp.messaging.outgoing." + Channels.OUTGOING_SITEMAPS + ".url",
          "http://" + getContainerLocalhost() + ":${quarkus.wiremock.devservices.port}"
          + OUTGOING_SITEMAPS_ENDPOINT,
          "streamx.blueprints.sitemap-generator.base-url", BASE_URL,
          "streamx.blueprints.sitemap-generator.output-key", SITEMAP_FILE_NAME,
          "streamx.blueprints.sitemap-generator.dirty-check.interval", "1s",
          "streamx.blueprints.sitemap-generator.dirty-check.delay", "1s"
      );
    }
  }

  private static String getContainerLocalhost() {
    if (System.getProperty("os.name", "").toLowerCase().startsWith("linux")) {
      return "172.17.0.1";
    } else {
      return "host.docker.internal";
    }
  }
}