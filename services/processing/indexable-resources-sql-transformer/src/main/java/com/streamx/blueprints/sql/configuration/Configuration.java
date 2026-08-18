package com.streamx.blueprints.sql.configuration;

import io.smallrye.config.ConfigMapping;
import java.util.List;
import java.util.Map;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-sql-transformer")
public interface Configuration {

  Map<String, List<String>> persistedData();

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
}
