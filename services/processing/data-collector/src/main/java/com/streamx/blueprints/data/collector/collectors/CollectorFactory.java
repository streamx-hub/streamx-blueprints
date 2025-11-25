package com.streamx.blueprints.data.collector.collectors;

import java.util.Map;

public interface CollectorFactory {

  String id();

  Collector create(DataFilter dataFilter, Map<String, String> properties);

}
