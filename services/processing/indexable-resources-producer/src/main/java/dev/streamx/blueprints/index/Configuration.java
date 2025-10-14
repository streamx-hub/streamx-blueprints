package dev.streamx.blueprints.index;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-producer")
public interface Configuration {

  @WithDefault("false")
  boolean indexFragments();
}
