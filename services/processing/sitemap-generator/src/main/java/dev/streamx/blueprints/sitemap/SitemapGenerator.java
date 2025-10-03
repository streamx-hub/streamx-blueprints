package dev.streamx.blueprints.sitemap;

import cz.jiripinkas.jsitemapgenerator.WebPage;
import cz.jiripinkas.jsitemapgenerator.WebPage.WebPageBuilder;
import dev.streamx.blueprints.sitemap.configuration.properties.SitemapGeneratorProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SitemapGenerator {

  @Inject
  Logger log;

  @Inject
  SitemapGeneratorProperties properties;

  public String generate(Collection<SitemapEntryData> entries) {
    var generator = createSitemapGenerator();
    AtomicLong counter = new AtomicLong(0);
    entries.forEach(sitemapEntryData -> {
      counter.incrementAndGet();
      addPageToGenerator(generator, sitemapEntryData);
    });
    log.debugf("Sitemap contains %s pages", counter.get());

    return generator.toString();
  }

  private void addPageToGenerator(
      cz.jiripinkas.jsitemapgenerator.generator.SitemapGenerator generator,
      SitemapEntryData sitemapEntryData) {
    WebPageBuilder builder = WebPage.builder()
        .name(sitemapEntryData.pageName());
    if (properties.generateLastmodAttribute()
            && sitemapEntryData.timestamp() != null
    ) {
      builder.lastMod(new Date(sitemapEntryData.timestamp()));
    }
    generator.addPage(builder.build());
  }

  private cz.jiripinkas.jsitemapgenerator.generator.SitemapGenerator createSitemapGenerator() {
    return cz.jiripinkas.jsitemapgenerator.generator.SitemapGenerator.of(
        properties.baseUrl()
    );
  }

  public record SitemapEntryData(String pageName, Long timestamp) {}
}
