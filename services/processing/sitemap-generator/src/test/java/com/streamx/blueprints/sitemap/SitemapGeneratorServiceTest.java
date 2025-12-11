package com.streamx.blueprints.sitemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SitemapGeneratorServiceTest {

  private static final long EVENT_TIME_INITIAL_VALUE = 1722265416000L;
  private static final long ONE_DAY_IN_MILLIS = 86400000;
  private static final AtomicLong EVENT_TIME = new AtomicLong(EVENT_TIME_INITIAL_VALUE);

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ProcessPageFunction processPageFunction;

  @InjectSpy
  PublishedPagesStore publishedPagesStore;

  private InMemorySource<CloudEvent> pages;
  private InMemorySink<CloudEvent> sitemapSink;

  @BeforeAll
  static void beforeAll() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @BeforeEach
  void setup() {
    pages = connector.source(Channels.INCOMING_PAGES);
    sitemapSink = connector.sink(Channels.OUTGOING_SITEMAPS);
  }

  @AfterEach
  void clearStore() {
    publishedPagesStore.clear();
  }

  @Test
  void shouldPublishSitemap() {
    // when
    requestSitemapGeneration();

    // then
    assertNoSitemap();
    cleanUp();

    // when
    unpublishPage("/published.html");
    requestSitemapGeneration();

    // then
    assertSitemap("""
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        </urlset>""");
    cleanUp();

    // when
    publishPage("/published.html");
    requestSitemapGeneration();

    // then
    assertSitemap("""
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        <url>
        <loc>https://test-domain.com/published.html</loc>
        <lastmod>2024-07-29T15:03:36+00:00</lastmod>
        </url>
        </urlset>""");
    cleanUp();

    // when
    unpublishPage("/published.html");
    requestSitemapGeneration();

    // then
    assertSitemap("""
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        </urlset>""");
    cleanUp();
  }

  @Test
  void shouldPublishSitemapFromPageEventWithNullTime() {
    // when
    sendPage("/page.html", Page.TYPE_PUBLISHED, null);
    requestSitemapGeneration();

    // then
    assertSitemap("""
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
        <url>
        <loc>https://test-domain.com/page.html</loc>
        </url>
        </urlset>""");
    cleanUp();
  }

  @Test
  void shouldNotGenerateSitemapFromPageWithNotMatchingPath() {
    // when
    publishPage("page.txt");

    // then
    assertNoSitemap();
    cleanUp();
  }

  private void cleanUp() {
    sitemapSink.clear();
    EVENT_TIME.set(EVENT_TIME_INITIAL_VALUE);
  }

  private void publishPage(String key) {
    sendPage(key, Page.TYPE_PUBLISHED);
  }

  private void unpublishPage(String key) {
    sendPage(key, Page.TYPE_UNPUBLISHED);
  }

  private void sendPage(String key, String eventType) {
    long eventTime = EVENT_TIME.getAndAdd(ONE_DAY_IN_MILLIS);
    sendPage(key, eventType, CloudEventUtils.toOffsetDateTime(eventTime));
  }

  private void sendPage(String key, String eventType, OffsetDateTime eventTime) {
    Page page = new Page("whatever", "type");
    CloudEvent pageEvent = CloudEventUtils.eventWithData(key, eventType, page, eventTime);
    pages.send(pageEvent);
    waitForEventProcessed(key, eventType, eventTime);
  }

  private void waitForEventProcessed(String key, String eventType, OffsetDateTime eventTime) {
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
        verify(publishedPagesStore).register(key, eventTime, eventType)
    );
    reset(publishedPagesStore);
  }

  /**
   * The method will publish sitemap if there are not included pages regardless of defined
   * <i>max-dirty-sequence-count</i> limit in properties
   */
  private void requestSitemapGeneration() {
    processPageFunction.publishSitemapIfNeeded();
    processPageFunction.publishSitemapIfNeeded();
  }

  private void assertNoSitemap() {
    assertThat(sitemapSink.received()).isEmpty();
  }

  private void assertSitemap(String expected) {
    assertThat(sitemapSink.received()).hasSize(1);

    CloudEvent sitemapEvent = sitemapSink.received().getFirst().getPayload();
    assertThat(sitemapEvent.getType()).isEqualTo(WebResource.TYPE_PUBLISHED);
    assertThat(sitemapEvent.getSubject()).isEqualTo("/sitemaps/test-domain.com/sitemap.xml");

    WebResource sitemap = CloudEventUtils.getData(sitemapEvent, WebResource.class);
    assertThat(sitemap).isNotNull();
    assertThat(sitemap.getContentAsString()).isEqualTo(expected);
    assertThat(sitemap.getType()).isEqualTo("sitemap-type");
  }

}
