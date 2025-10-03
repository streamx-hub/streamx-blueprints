package dev.streamx.blueprints.sitemap;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.sitemap.SitemapGenerator.SitemapEntryData;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.Store.Entry;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SitemapService {

  @FromChannel(Channels.INCOMING_PAGES_CHANNEL)
  Store<PageEntry> pageStore;

  @Inject
  SitemapGenerator sitemapGenerator;

  @Inject
  PageKeyService pageKeyService;

  public WebResource createSitemapResource() {

    var entries = pageStore.entriesWithMetadata()
        .filter(e -> e.value() != null)
        .map(Entry::value)
        .filter(this::isValidForSitemapGeneration)
        .map(this::generateSitemapEntryData)
        .toList();

    String sitemap = sitemapGenerator.generate(entries);
    return new WebResource(sitemap);
  }

  private SitemapEntryData generateSitemapEntryData(GenericPayload<PageEntry> entry) {
    var timestamp = entry.getMetadata()
        .get(EventTime.class)
        .map(EventTime::getValue)
        .orElse(null);
    var pageName = entry.getPayload().getPageName();
    return new SitemapEntryData(pageName, timestamp);
  }

  private boolean isValidForSitemapGeneration(GenericPayload<PageEntry> entry) {
    return entry.getMetadata() != null
        && entry.getPayload() != null
        && entry.getPayload().isPublished()
        && pageKeyService.isSupportedKey(entry.getPayload().getPageName());
  }
}
