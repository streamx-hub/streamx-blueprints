package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping.WebResources;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebResourcesService {

  @Inject
  Logger log;

  @Inject
  ServiceConfigMapping serviceConfiguration;

  private final List<Pattern> filters = new CopyOnWriteArrayList<>();

  @Startup
  void init() {
    serviceConfiguration.webResources()
        .map(WebResources::filters)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .orElse(Collections.emptyList()).stream()
        .map(Pattern::compile)
        .forEach(filters::add);
    log.infof("Finished loading configuration for web resources filter: %s",
        String.join(", ", filters.stream().map(Pattern::toString).toList()));
  }

  boolean isMatchingFilter(Key key) {
    return filters.stream().anyMatch(predicate -> predicate.matcher(key.getValue()).matches());
  }

  String mapToWebResourceKey(Key dataKey) {
    String prefix = serviceConfiguration.webResources()
        .map(WebResources::outgoingPrefix)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .orElse("");
    return prefix + dataKey.getValue() + ".json";
  }
}
