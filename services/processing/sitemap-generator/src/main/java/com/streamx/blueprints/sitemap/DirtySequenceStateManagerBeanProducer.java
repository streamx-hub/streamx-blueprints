package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.sitemap.configuration.Configuration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class DirtySequenceStateManagerBeanProducer {

  @Inject
  Configuration properties;

  @Produces
  DirtySequenceStateManager produceDirtySequenceStateManager() {
    return new DirtySequenceStateManager(properties.dirtyCheck().maxDirtySequenceCount());
  }
}
