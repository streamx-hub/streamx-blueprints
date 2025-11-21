package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.CollectorFactory;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.configuration.CollectorProperties;
import com.streamx.blueprints.data.collector.stores.PublishedDataStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
  public Collector create(DataFilter dataFilter, CollectorProperties properties) {
    log.infof("Creating collector %s with config %s", id(), StringUtils.join(properties));

    List<String> filterBy = properties.filterBy()
        .map(s -> StringUtils.split(s, ','))
        .map(Arrays::stream)
        .map(Stream::toList)
        .orElse(Collections.emptyList());

    return new AggregateByPropertyValueCollector(
        dataStore,
        dataFilter,
        properties.outputKeyPrefix().orElseThrow(),
        filterBy,
        properties.groupBy().orElse(null),
        properties.sortBy().orElse(null),
        SortMode.valueOf(
            properties.sortMode().orElse(SortMode.ASC.toString()).toUpperCase()),
        Integer.parseInt(properties.max().orElse("10")));
  }
}
