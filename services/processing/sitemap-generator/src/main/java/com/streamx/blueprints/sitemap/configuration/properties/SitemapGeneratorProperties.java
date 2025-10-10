package com.streamx.blueprints.sitemap.configuration.properties;

import io.smallrye.config.ConfigMapping;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "streamx.blueprints.sitemap-generator")
public interface SitemapGeneratorProperties {

  Map<String, String> matchKeyPatterns();

  String baseUrl();

  String outputKey();

  Optional<String> outputType();

  boolean generateLastmodAttribute();

  DirtyCheck dirtyCheck();

  interface DirtyCheck {
    Long maxDirtySequenceCount();

    String interval();

    String delay();
  }
}
