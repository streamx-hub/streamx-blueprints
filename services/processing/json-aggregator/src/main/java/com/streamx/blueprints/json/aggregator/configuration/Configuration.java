package com.streamx.blueprints.json.aggregator.configuration;

import io.smallrye.config.ConfigMapping;
import java.util.Map;

@ConfigMapping(prefix = "streamx.blueprints.json-aggregator")
public interface Configuration {

  Map<String, AggregationConfiguration> configurations();
}
