package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sitemap.SitemapGenerator.SitemapEntryData;
import com.streamx.blueprints.sitemap.configuration.Configuration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SitemapService {

  @Inject
  PublishedPagesStore publishedPagesStore;

  @Inject
  SitemapGenerator sitemapGenerator;

  @Inject
  PageKeyService pageKeyService;

  @Inject
  Configuration configuration;

  public WebResource createSitemapResource() {
    var entries = publishedPagesStore.getPages()
        .filter(this::isValidForSitemapGeneration)
        .map(this::generateSitemapEntryData)
        .toList();

    String sitemap = sitemapGenerator.generate(entries);
    String outputType = configuration.outputType().orElse(null);
    return new WebResource(sitemap, outputType);
  }

  private SitemapEntryData generateSitemapEntryData(PublishedPage entry) {
    return new SitemapEntryData(entry.pageName(), entry.timestamp());
  }

  private boolean isValidForSitemapGeneration(PublishedPage entry) {
    return pageKeyService.isSupportedKey(entry.pageName());
  }
}
