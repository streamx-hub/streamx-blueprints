package com.streamx.blueprints.data.collector.configuration;

import java.util.Optional;

public interface CollectionConfiguration {

  String collector();

  Optional<String> dataKeyMatchPattern();

  Optional<String> dataTypeMatchPattern();

  Optional<String> outputDataType();

  CollectorProperties properties();

}
