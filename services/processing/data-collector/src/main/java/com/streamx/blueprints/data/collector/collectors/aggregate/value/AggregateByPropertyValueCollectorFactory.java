package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.streamx.blueprints.data.collector.Channels;
import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.collectors.aggregate.value.AggregateByPropertyValueCollector.SortMode;
import dev.streamx.blueprints.data.Data;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AggregateByPropertyValueCollectorFactory implements CollectorFactory {

  @Inject
  Logger log;

  @FromChannel(Channels.Incoming.DATA)
  Store<Data> dataStore;

  @Override
  public String id() {
    return "aggregate-by-property-value";
  }

  @Override
  public Collector create(DataFilter dataFilter, Map<String, String> properties) {
    log.infof("Creating collector %s with config %s", id(), StringUtils.join(properties));

    String[] filterBy = Optional.ofNullable(properties)
        .filter(p -> p.containsKey("filter-by"))
        .map(p -> p.get("filter-by"))
        .map(s -> StringUtils.split(s, ','))
        .orElse(null);

    return new AggregateByPropertyValueCollector(
        dataStore,
        dataFilter,
        Objects.requireNonNull(properties.get("output-key-prefix")),
        filterBy,
        properties.get("group-by"),
        properties.get("sort-by"),
        SortMode.valueOf(
            properties.getOrDefault("sort-mode", SortMode.ASC.toString()).toUpperCase()),
        Integer.parseInt(properties.getOrDefault("max", "10")));
  }
}
