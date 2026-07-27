package com.streamx.blueprints.index.configuration;

import java.util.List;
import java.util.Optional;

public interface FieldsHtmlElementCollectorConfig extends BaseHtmlElementCollectorConfig {

  Optional<List<String>> allowedKeyValues();
}
