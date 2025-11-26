package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.Collector.CollectedOutput;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.configuration.CollectionConfiguration;
import com.streamx.blueprints.data.collector.configuration.Configuration;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Collectors {

  @Inject
  Logger log;

  @Inject
  Configuration serviceConfiguration;

  @Inject
  @All
  List<CollectorFactory> factories;

  private final List<Collection> collections = new CopyOnWriteArrayList<>();

  @Startup
  void init() {
    serviceConfiguration.configurations().values().stream()
        .map(this::processConfig)
        .forEach(collections::add);
    log.infof("Finished loading configuration for application  config: %s",
        String.join(", ", collections.stream().map(Collection::toString).toList()));
  }

  private Collection processConfig(CollectionConfiguration configuration)
      throws IllegalArgumentException {
    log.tracef("Processing collection configuration %s", configuration);
    DataFilter dataFilter = createDataFilter(configuration);
    return new Collection(
        dataFilter,
        configuration.outputDataType(),
        factories.stream()
            .filter(factory -> factory.id().equals(configuration.collector()))
            .findFirst()
            .map(factory -> factory.create(dataFilter,
                configuration.properties()))
            .orElseThrow(() -> new IllegalArgumentException(
                "No factory for collector " + configuration.collector())),
        new AtomicBoolean(false));
  }

  private DataFilter createDataFilter(CollectionConfiguration configuration) {
    String dataKeyMatchPatternValue = configuration.dataKeyMatchPattern().orElse(null);
    String dataTypeMatchPatternValue = configuration.dataTypeMatchPattern().orElse(null);
    if (ObjectUtils.allNull(dataKeyMatchPatternValue, dataTypeMatchPatternValue)) {
      throw new IllegalStateException(
          "Match pattern for data key or type must be set for configuration " + configuration);
    }
    Pattern dataKeyMatchPattern = compilePattern(dataKeyMatchPatternValue);
    Pattern dataTypeMatchPattern = compilePattern(dataTypeMatchPatternValue);
    return new PatternsDataFilter(dataKeyMatchPattern, dataTypeMatchPattern);
  }

  private Pattern compilePattern(String patternValue) {
    return Optional.ofNullable(patternValue).map(Pattern::compile).orElse(null);
  }

  public boolean processData(String key, Data data, String eventType) {
    log.tracef("Processing data [key=%s] [eventType=%s]", key, eventType);
    String dataType = Optional.ofNullable(data).map(Data::getType).orElse(null);
    AtomicBoolean dirty = new AtomicBoolean(false);
    collections.stream()
        .filter(collection -> collection.dataFilter.test(key, dataType))
        .forEach(collection -> {
          if (collection.collector.process(key, data, eventType)) {
            collection.dirty.set(true);
            dirty.set(true);
          }
        });
    return dirty.get();
  }

  public List<CollectedOutput> collect() {
    log.trace("Collecting data");
    return collections.stream()
        .filter(collection -> collection.dirty.getAndSet(false))
        .flatMap(collection -> collection.collector.collect().stream()
            .map(collectedOutput -> new CollectedOutput(
                collectedOutput.key(),
                collectedOutput.dataContent(),
                collection.outputType.orElse(collectedOutput.dataType()))))
        .toList();
  }

  record Collection(DataFilter dataFilter, Optional<String> outputType, Collector collector,
                    AtomicBoolean dirty) {

  }

}
