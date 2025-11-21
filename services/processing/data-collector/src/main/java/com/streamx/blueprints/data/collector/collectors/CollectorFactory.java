package com.streamx.blueprints.data.collector.collectors;

import com.streamx.blueprints.data.collector.configuration.CollectorProperties;

public interface CollectorFactory {

  String id();

  Collector create(DataFilter dataFilter, CollectorProperties properties);

}
