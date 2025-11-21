package com.streamx.blueprints.data.collector.configuration;

import java.util.List;
import java.util.Optional;

public interface WebResources {

  Optional<List<String>> filters();

  Optional<String> outgoingPrefix();
}
