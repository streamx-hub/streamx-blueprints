package com.streamx.blueprints.index.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;

@ConfigMapping(prefix = "streamx.blueprints.indexable-resources-producer")
public interface Configuration {

  @WithDefault("false")
  boolean indexFragments();

  @WithDefault("false")
  boolean includeFacets();

  @WithDefault("false")
  boolean includeFields();

  Map<String, FacetsHtmlElementCollectorConfig> facetsConfiguration();

  Map<String, FieldsHtmlElementCollectorConfig> fieldsConfiguration();
}
