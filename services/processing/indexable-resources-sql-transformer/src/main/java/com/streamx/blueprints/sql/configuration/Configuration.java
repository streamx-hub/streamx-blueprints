package com.streamx.blueprints.sql.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-sql-transformer")
public interface Configuration {

  PersistedData persistedData();

  DirtyCheck dirtyCheck();

  interface DirtyCheck {

    Long maxDirtySequenceCount();

    String interval();

    String delay();
  }

  Map<String, Transformation> transformations();

  interface Transformation {

    String sqlQuery();
  }

  interface PersistedData {

    @WithDefault("false")
    boolean includeContent();

    Optional<List<String>> fields();

    Optional<List<String>> facets();
  }
}
