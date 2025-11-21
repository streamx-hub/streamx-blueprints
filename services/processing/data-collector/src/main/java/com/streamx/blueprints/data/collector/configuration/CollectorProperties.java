package com.streamx.blueprints.data.collector.configuration;

import java.util.Optional;

public interface CollectorProperties {

  Optional<String> filterBy();

  Optional<String> outputKeyPrefix();

  Optional<String> groupBy();

  Optional<String> sortBy();

  Optional<String> sortMode();

  Optional<String> max();

}
