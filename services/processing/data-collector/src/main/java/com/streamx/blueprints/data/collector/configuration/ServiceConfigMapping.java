package com.streamx.blueprints.data.collector.configuration;

import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.data-collector")
public interface ServiceConfigMapping {

  Optional<WebResources> webResources();

  Map<String, CollectionConfiguration> configurations();

  DirtyCheck dirtyCheck();

  interface DirtyCheck {

    Long maxDirtySequenceCount();

    String interval();

    String delay();
  }

  interface WebResources {

    Optional<List<String>> filters();

    Optional<String> outgoingPrefix();
  }
}
