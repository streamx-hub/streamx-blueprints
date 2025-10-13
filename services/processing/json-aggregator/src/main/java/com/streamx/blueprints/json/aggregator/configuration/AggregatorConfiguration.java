package com.streamx.blueprints.json.aggregator.configuration;

import io.smallrye.config.ConfigMapping;
import java.util.List;

@ConfigMapping(prefix = "streamx.blueprints.json-aggregator")
public interface AggregatorConfiguration {

  List<Configuration> configurations();
}
