package com.streamx.blueprints.index.configuration;

import io.smallrye.config.WithDefault;
import java.util.List;
import java.util.Optional;

public interface HtmlElementCollectorConfiguration {

  Optional<String> selector();

  Optional<List<String>> keys();

  Optional<List<String>> values();

  @WithDefault("false")
  boolean singleAttr();

  Optional<String> keyDelimiter();

  Optional<String> hierarchicalFacetDelimiter();

  Optional<List<String>> indexedKeys();

  @WithDefault("false")
  boolean isFacet();
}
