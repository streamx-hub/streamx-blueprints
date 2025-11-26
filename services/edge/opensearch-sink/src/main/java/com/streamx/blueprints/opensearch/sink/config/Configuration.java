package com.streamx.blueprints.opensearch.sink.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;

@ConfigMapping(prefix = "streamx.blueprints.opensearch-sink")
public interface Configuration {

  @WithDefault("true")
  boolean typeRequired();

  @WithDefault("classpath:opensearch/service-init")
  List<String> migrationScriptLocations();

  @WithDefault("")
  List<String> allowedJsonPaths();

}
