package com.streamx.blueprints.data.collector.configuration;

import java.util.Map;
import java.util.Optional;

public interface CollectionConfiguration {

  String collector();

  Optional<String> dataKeyMatchPattern();

  Optional<String> dataTypeMatchPattern();

  Optional<String> outputDataType();

  Map<String, String> properties();

}
