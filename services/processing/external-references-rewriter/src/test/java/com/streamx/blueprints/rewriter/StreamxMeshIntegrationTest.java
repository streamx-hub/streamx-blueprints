package com.streamx.blueprints.rewriter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.data.WebResource;
import com.streamx.clients.ingestion.StreamxClient;
import com.streamx.clients.ingestion.exceptions.StreamxClientException;
import com.streamx.clients.ingestion.publisher.Publisher;
import io.cloudevents.CloudEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Reader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * This test is disabled for mvn builds, but can be launched manually - e.g., via IDE. Prerequisite
 * to run this test: - running instance of StreamX, started with the project's /mesh.yaml file
 */
@Disabled
public class StreamxMeshIntegrationTest {

  private static final String INGESTION_URL = "http://localhost:8080";
  private static final String DELIVERY_URL = "http://localhost:8081";

  @BeforeAll
  static void setApplicationName() {
    System.setProperty("quarkus.application.name",
        StreamxMeshIntegrationTest.class.getSimpleName());
  }

  @Test
  void shouldPublishSitemapFileAlongWithPagesAndTheirImages() throws Exception {
    // given
    final String key = "streamx-tech-sitemap.xml";
    final String payloadType = "web-resource";
    final String content = """
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>https://www.streamx.tech/blog.html</loc>
          </url>
          <url>
            <loc>https://www.streamx.tech/categories/accent-furniture.html</loc>
          </url>
        </urlset>""";

    WebResource sitemapResource = new WebResource(content, payloadType);

    // when
    send(key, sitemapResource, WebResource.TYPE_PUBLISHED);

    // then
    assertAllUrlsAreAccessible(
        // the sitemap file
        DELIVERY_URL + "/streamx-tech-sitemap.xml",
        // two pages from the sitemap file
        DELIVERY_URL + "/blog.html",
        DELIVERY_URL + "/categories/accent-furniture.html",
        // referenced image
        DELIVERY_URL + "/assets/logo-footer.webp"
    );
  }

  @Test
  void shouldComposePage() throws Exception {
    // given
    String layoutKey = "layout-for-foobar-pages";
    String layout = """
        <html>
          Hello {{#insert name="foo.html"}}.
          This is {{#insert name="bar.html"}}.
          FAQs:
            {{#insert name="faq.html"}}
              No data
            {{}}
        </html>
        """;

    String compositionKey = "/composition-for-john-streamx-foobar-page.html";
    String composition = """
        {{#define name="foo.html"}}
        <b>John</b>
        the
        developer

        {{#define name="bar.html"}}
        <b>StreamX</b>
        and how to use it
        """;

    // when
    send(layoutKey, new Layout(layout), Layout.TYPE_PUBLISHED);
    send(compositionKey, new Composition(composition, null, layoutKey), Composition.TYPE_PUBLISHED);

    // then
    assertUrlContent(DELIVERY_URL + compositionKey, """
        <html>
          Hello <b>John</b>
        the
        developer.
          This is <b>StreamX</b>
        and how to use it.
          FAQs:
            No data
        </html>
        """);
  }

  @Test
  void shouldOptimizeImage() throws Exception {
    // given
    String imageKey = "/images/wikipedia.png";
    String imagePath = "../image-optimizer/src/test/resources/ds.png";
    Asset image = new Asset(FileUtils.readFileToByteArray(new File(imagePath)));
    send(imageKey, image, Asset.TYPE_PUBLISHED);
    sleepOneSecond();

    // when
    String pageKey = "/page-with-wikipedia-image.html";
    Page page = new Page("Hello. <img src='/images/wikipedia.png' />");
    send(pageKey, page, Page.TYPE_PUBLISHED);

    // then
    assertAllUrlsAreAccessible(
        DELIVERY_URL + imageKey,
        DELIVERY_URL + "/images/wikipedia-optimized.webp",
        DELIVERY_URL + pageKey
    );

    // and
    assertUrlContent(DELIVERY_URL + pageKey, """
        <html>
         <head></head>
         <body>
          Hello. <img src="/images/wikipedia-optimized.webp">
         </body>
        </html>""");
  }

  @Test
  void shouldGenerateSitemap() throws Exception {
    // given
    send("/page-1.html", new Page("Page 1"), Page.TYPE_PUBLISHED);
    send("/page-2.html", new Page("Page 2"), Page.TYPE_PUBLISHED);
    send("/page-3.html", new Page("Page 3"), Page.TYPE_PUBLISHED);

    // then
    assertAllUrlsAreAccessible(
        DELIVERY_URL + "/page-1.html",
        DELIVERY_URL + "/page-2.html",
        DELIVERY_URL + "/page-3.html",
        DELIVERY_URL + "/sitemap.xml"
    );

    // and
    assertUrlContentLines(DELIVERY_URL + "/sitemap.xml", List.of(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
        "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">",
        "<url>",
        "<loc>http://localhost:8081/page-1.html</loc>",
        "</url>",
        "<url>",
        "<loc>http://localhost:8081/page-2.html</loc>",
        "</url>",
        "<url>",
        "<loc>http://localhost:8081/page-3.html</loc>",
        "</url>",
        "</urlset>"
    ));
  }

  @Test
  // TODO: before merging, remove this class from repo
  // TODO: before merging, rename packages to com.streamx
  // TODO: before merging, remove test dependencies from pom.xml
  void shouldProduceIndexableResources() throws Exception {
    // given
    String pageHtml = """
        <head>
            <title>Title</title>
        </head>
        <body>
            <h1>H1</h1>
            <h2>H2</h2>
            <p>Paragraph</p>
        </body>
        """;

    // when
    send("/hello-page.html", new Page(pageHtml), Page.TYPE_PUBLISHED);

    // then
    List<String> resultJsons = readAllJsonsFromTopic("indexable-resources");
    assertThat(resultJsons).contains("""
        {"title":"Title","content":"Title\\n\\n\\n    H1\\n\\n    H2\\n\\n    Paragraph"}""");
  }

  @Test
  void shouldRenderPageFromAggregatedData() throws Exception {
    // given
    final int productId = 1;
    final int productPrice = 200;
    final String productType = "product/variant";

    // and: publish product data and price to be aggregated
    send(
        "pim:" + productId,
        new Data("{\"id\":\"%s\"}".formatted(productId), productType),
        Data.TYPE_PUBLISHED
    );
    send(
        "price:" + productId,
        new Data("{\"price\":\"%d\"}".formatted(productPrice)),
        Data.TYPE_PUBLISHED
    );
    sleepOneSecond();

    // and: publish RenderingContext
    String rendererKey = "test-renderer";
    RenderingContext renderingContext = new RenderingContext(
        rendererKey,
        "product:.*",
        productType,
        "product-pages/{{id}}.html",
        null,
        OutputFormat.PAGE
    );

    // and: publish Renderer
    String renderingContextKey = "test-rendering-context";
    send(renderingContextKey, renderingContext, RenderingContext.TYPE_PUBLISHED);
    sleepOneSecond();

    Renderer renderer = new Renderer("Product with ID {{id}} has price {{price}}");
    send(rendererKey, renderer, Renderer.TYPE_PUBLISHED);
    sleepOneSecond();

    // then: expect rendered page
    assertUrlContent(
        DELIVERY_URL + "/product-pages/" + productId + ".html",
        "Product with ID " + productId + " has price " + productPrice
    );
  }

  private void send(String key, Object resource, String eventType) throws StreamxClientException {
    CloudEvent event = CloudEventUtils.eventWithData(key, eventType, resource);
    try (StreamxClient client = StreamxClient.create(INGESTION_URL)) {
      Publisher publisher = client.newPublisher();
      publisher.send(event);
    }
  }

  private static void sleepOneSecond() {
    ThreadUtils.sleepQuietly(Duration.ofSeconds(1));
  }

  private static String assertUrlsIsAccessible(String url) throws IOException {
    return assertAllUrlsAreAccessible(url).get(url);
  }

  private static Map<String, String> assertAllUrlsAreAccessible(String... urls) throws IOException {
    Map<String, String> urlContentMap = new LinkedHashMap<>();
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      for (String url : urls) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
              HttpGet request = new HttpGet(url);
              CloseableHttpResponse response = httpClient.execute(request);
              int status = response.getStatusLine().getStatusCode();
              String body = IOUtils.toString(response.getEntity().getContent(), UTF_8);

              assertThat(status)
                  .describedAs(url)
                  .isEqualTo(200);

              assertThat(body)
                  .describedAs(url)
                  .isNotEmpty();

              urlContentMap.put(url, body);
            }
        );
      }
    }
    return urlContentMap;
  }

  private static void assertUrlContent(String url, String expectedContent) throws IOException {
    String actualContent = assertUrlsIsAccessible(url);
    assertThat(actualContent).isEqualTo(expectedContent);
  }

  private static void assertUrlContentLines(String url, List<String> expectedContentLines)
      throws IOException {
    String actualContent = assertUrlsIsAccessible(url);
    assertThat(actualContent.lines()).containsSubsequence(expectedContentLines);
  }

  private static List<String> readAllJsonsFromTopic(String topic) throws Exception {
    List<String> result = new LinkedList<>();
    String fullTopic = "persistent://streamx/outboxes/" + topic;

    try (PulsarClient client = PulsarClient.builder().serviceUrl("pulsar://localhost:6650").build();
        Reader<byte[]> reader = client.newReader().topic(fullTopic)
            .startMessageId(MessageId.earliest).create()) {

      while (reader.hasMessageAvailable()) {
        Message<byte[]> msg = reader.readNext();
        String payload = new String(msg.getData());
        String json = "{" + StringUtils.substringBetween(payload, "{", "}") + "}";

        String base64Content = new ObjectMapper().readTree(json).get("content").asText();
        byte[] decoded = Base64.getDecoder().decode(base64Content);
        String content = new String(decoded, UTF_8);
        result.add(content);
      }
    }

    return result;
  }
}