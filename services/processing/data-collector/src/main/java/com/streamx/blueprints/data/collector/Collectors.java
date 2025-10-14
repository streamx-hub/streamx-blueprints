package com.streamx.blueprints.data.collector;

import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.Collector.CollectedOutput;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.configuration.CollectionConfiguration;
import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.quarkus.arc.All;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Collectors {

  @Inject
  Logger log;

  @Inject
  ServiceConfigMapping serviceConfiguration;

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
    if (dataKeyMatchPatternValue == null && dataTypeMatchPatternValue == null) {
      throw new IllegalStateException(
          "Match pattern for key or data must be set for configuration " + configuration);
    }
    Pattern dataKeyMatchPattern =
        dataKeyMatchPatternValue != null ? Pattern.compile(dataKeyMatchPatternValue) : null;
    Pattern dataTypeMatchPattern =
        dataTypeMatchPatternValue != null ? Pattern.compile(dataTypeMatchPatternValue) : null;
    return new PatternsDataFilter(dataKeyMatchPattern, dataTypeMatchPattern);
  }

  public boolean processData(Key key, Data data, Action action, Properties properties) {
    log.tracef("Processing data [key=%s] [action=%s]", key, action);
    AtomicBoolean dirty = new AtomicBoolean(false);
    collections.stream()
        .filter(collection -> collection.dataFilter.test(key.getValue(),
            properties.getType().orElse(null)))
        .forEach(collection -> {
          if (collection.collector.process(key, data, action)) {
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
                collection.outputType.orElse(collectedOutput.type()),
                collectedOutput.data())))
        .toList();
  }

  record Collection(DataFilter dataFilter, Optional<String> outputType, Collector collector,
                    AtomicBoolean dirty) {

  }

}
