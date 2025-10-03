package dev.streamx.blueprints.sitemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import dev.streamx.quasar.reactive.messaging.utils.MetadataUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SitemapGeneratorServiceTest {

  private static final long EVENT_TIME_INITIAL_VALUE = 1722265416000L;
  private static final long ONE_DAY_IN_MILIS = 86400000;
  private static final AtomicLong EVENT_TIME = new AtomicLong(EVENT_TIME_INITIAL_VALUE);

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ProcessPageFunction processPageFunction;

  private InMemorySource<Message<Page>> pages;
  private InMemorySink<WebResource> sitemapSink;

  @BeforeAll
  static void beforeAll() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @BeforeEach
  void setup() {
    pages = connector.source(Channels.INCOMING_PAGES_CHANNEL);
    sitemapSink = connector.sink(Channels.OUTGOING_SITEMAPS_CHANNEL);
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

  private void cleanUp() {
    sitemapSink.clear();
    EVENT_TIME.set(EVENT_TIME_INITIAL_VALUE);
  }

  private void publishPage(String key) {
    sendPage(key, Action.PUBLISH);
  }

  private void unpublishPage(String key) {
    sendPage(key, Action.UNPUBLISH);
  }

  private void sendPage(String key, Action action) {
    AtomicBoolean isAcked = new AtomicBoolean(false);
    pages.send(Message.of(new Page("whatever"), Metadata.of(
            Key.of(key),
            EventTime.of(EVENT_TIME.getAndAdd(ONE_DAY_IN_MILIS)),
            action))
        .withAck(() -> {
          isAcked.set(true);
          return CompletableFuture.completedFuture(null);
        }));
    // Wait till message is acked (processed) as depending on signature of method processing the
    // message the processing may be synchronous or asynchronous.
    await().until(isAcked::get);
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
    List<? extends Message<WebResource>> received = sitemapSink.received();
    assertThat(received).isEmpty();
  }

  private void assertSitemap(String expected) {
    List<? extends Message<WebResource>> received = sitemapSink.received();
    assertThat(received).hasSize(1);
    Message<WebResource> sitemap = received.get(0);
    assertThat(sitemap.getPayload().getContentAsString()).isEqualTo(expected);
    assertThat(MetadataUtils.extractAction(sitemap)).isEqualTo(Action.PUBLISH);
    assertThat(MetadataUtils.extractKey(sitemap)).isEqualTo("sitemap.xml");
    assertThat(Properties.from(sitemap).getType().orElse(null)).isEqualTo("sitemap-type");
  }
}
