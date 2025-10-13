package com.streamx.blueprints.json.aggregator.configuration;

import java.util.List;
import java.util.Optional;

public interface Configuration {

  String masterNamespace();

  Optional<List<String>> optionalNamespaces();

  Optional<String> outputType();

  String outputNamespace();
}
