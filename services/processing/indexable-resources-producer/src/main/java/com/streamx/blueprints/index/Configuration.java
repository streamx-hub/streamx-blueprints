package com.streamx.blueprints.index;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-producer")
public interface Configuration {

  @WithDefault("false")
  boolean indexFragments();

  @WithDefault("false")
  boolean includeFacets();

  Metadata metadata();

  interface Metadata {
    Optional<String> selector();

    Optional<List<String>> keys();

    Optional<List<String>> values();

    Optional<String> keyDelimiter();
  }
}
