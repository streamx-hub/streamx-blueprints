package dev.streamx.blueprints.sitemap;

import com.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.sitemap.SitemapGenerator.SitemapEntryData;
import dev.streamx.blueprints.sitemap.configuration.properties.SitemapGeneratorProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class SitemapService {

  @Inject
  PublishedPagesStore publishedPagesStore;

  @Inject
  SitemapGenerator sitemapGenerator;

  @Inject
  PageKeyService pageKeyService;

  @Inject
  SitemapGeneratorProperties configuration;

  public WebResource createSitemapResource() {
    var entries = publishedPagesStore.getEntries().stream()
        .filter(this::isValidForSitemapGeneration)
        .map(this::generateSitemapEntryData)
        .toList();

    String sitemap = sitemapGenerator.generate(entries);
    String outputType = configuration.outputType().orElse(null);
    return new WebResource(sitemap, outputType);
  }

  private SitemapEntryData generateSitemapEntryData(PublishedPage entry) {
    Long timestamp = Optional.ofNullable(entry.time())
        .map(time -> time.toInstant().toEpochMilli())
        .orElse(null);
    var pageName = entry.pageName();
    return new SitemapEntryData(pageName, timestamp);
  }

  private boolean isValidForSitemapGeneration(PublishedPage entry) {
    return pageKeyService.isSupportedKey(entry.pageName());
  }
}
