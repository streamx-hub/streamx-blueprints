package com.streamx.blueprints.index.configuration;

import java.util.Optional;

public interface FacetsHtmlElementCollectorConfig extends BaseHtmlElementCollectorConfig {

  Optional<String> keyDelimiter();

  Optional<String> hierarchicalFacetDelimiter();
}
