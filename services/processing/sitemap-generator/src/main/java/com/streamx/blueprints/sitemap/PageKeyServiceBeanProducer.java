package com.streamx.blueprints.sitemap;

import com.streamx.blueprints.sitemap.configuration.Configuration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.Collection;

@ApplicationScoped
public class PageKeyServiceBeanProducer {

  @Inject
  Configuration properties;

  @Produces
  PageKeyService pageKeyService() {
    Collection<String> patterns = properties.matchKeyPatterns().values();
    return new PageKeyService(patterns.toArray(new String[0]));
  }
}
