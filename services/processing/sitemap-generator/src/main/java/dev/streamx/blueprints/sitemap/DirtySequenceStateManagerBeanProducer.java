package dev.streamx.blueprints.sitemap;

import dev.streamx.blueprints.sitemap.configuration.properties.SitemapGeneratorProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class DirtySequenceStateManagerBeanProducer {

  @Inject
  SitemapGeneratorProperties properties;

  @Produces
  DirtySequenceStateManager produceDirtySequenceStateManager() {
    return new DirtySequenceStateManager(properties.dirtyCheck().maxDirtySequenceCount());
  }
}
