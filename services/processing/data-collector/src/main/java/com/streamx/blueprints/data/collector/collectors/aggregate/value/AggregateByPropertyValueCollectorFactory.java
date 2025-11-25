package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.stores.PublishedDataStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AggregateByPropertyValueCollectorFactory implements CollectorFactory {

  @Inject
  Logger log;

  @Inject
  PublishedDataStore dataStore;

  @Override
  public String id() {
    return "aggregate-by-property-value";
  }

  @Override
  public Collector create(DataFilter dataFilter, Map<String, String> properties) {
    log.infof("Creating collector %s with config %s", id(), StringUtils.join(properties));

    List<String> filterBy = Optional.ofNullable(properties.get("filterby"))
        .map(s -> StringUtils.split(s, ','))
        .map(Arrays::stream)
        .map(Stream::toList)
        .orElse(Collections.emptyList());

    return new AggregateByPropertyValueCollector(
        dataStore,
        dataFilter,
        Objects.requireNonNull(properties.get("outputkeyprefix")),
        filterBy,
        properties.get("groupby"),
        properties.get("sortby"),
        SortMode.valueOf(
            properties.getOrDefault("sortmode", SortMode.ASC.toString()).toUpperCase()),
        Integer.parseInt(properties.getOrDefault("max", "10")));
  }
}
