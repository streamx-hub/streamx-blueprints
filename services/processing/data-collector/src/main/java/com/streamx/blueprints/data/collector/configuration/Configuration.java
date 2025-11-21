package com.streamx.blueprints.data.collector.configuration;

import io.smallrye.config.ConfigMapping;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.data-collector")
public interface Configuration {

  Optional<WebResources> webResources();

  Map<String, CollectionConfiguration> configurations();

  DirtyCheck dirtyCheck();

}
